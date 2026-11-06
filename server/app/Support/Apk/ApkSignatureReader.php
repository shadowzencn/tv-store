<?php

namespace App\Support\Apk;

use UnexpectedValueException;
use ZipArchive;

/**
 * Extracts the current signer's certificate (DER) from an APK. Signatures are not
 * cryptographically verified here; Android verifies them at install time.
 *
 * @internal
 */
final class ApkSignatureReader
{
    private const BLOCK_MAGIC = 'APK Sig Block 42';

    private const V2_BLOCK_ID = 0x7109871A;

    private const V3_BLOCK_ID = 0xF05368C0;

    private const EOCD_SIGNATURE = "PK\x05\x06";

    private const EOCD_MIN_SIZE = 22;

    private const MAX_COMMENT_SIZE = 0xFFFF;

    private const MAX_SIGNING_BLOCK_SIZE = 64 * 1024 * 1024;

    /**
     * @throws ApkInspectionException when the APK has no usable signature
     */
    public function signerCertificate(string $path, int $fileSize, ZipArchive $zip): string
    {
        try {
            $pairs = $this->signingBlockPairs($path, $fileSize);
            foreach ([self::V3_BLOCK_ID, self::V2_BLOCK_ID] as $id) {
                if (isset($pairs[$id])) {
                    return $this->firstCertificate($pairs[$id]);
                }
            }
        } catch (UnexpectedValueException $e) {
            throw new ApkInspectionException('APK 签名块（v2/v3）已损坏，无法读取签名证书。', previous: $e);
        }

        return $this->jarSignerCertificate($zip);
    }

    /** @return array<int, string> ID-value pairs; empty when the APK has no signing block */
    private function signingBlockPairs(string $path, int $fileSize): array
    {
        $handle = fopen($path, 'rb');
        if ($handle === false) {
            throw new UnexpectedValueException('Unable to open APK');
        }

        try {
            $centralDirectoryOffset = $this->centralDirectoryOffset($handle, $fileSize);
            if ($centralDirectoryOffset === null || $centralDirectoryOffset < 32) {
                return [];
            }

            $footer = $this->readAt($handle, $centralDirectoryOffset - 24, 24);
            if (substr($footer, 8) !== self::BLOCK_MAGIC) {
                return [];
            }
            $blockSize = (new BinaryReader($footer))->u64(0);
            if ($blockSize < 24 || $blockSize > self::MAX_SIGNING_BLOCK_SIZE || $blockSize + 8 > $centralDirectoryOffset) {
                throw new UnexpectedValueException('Invalid signing block size');
            }

            $block = new BinaryReader($this->readAt($handle, $centralDirectoryOffset - $blockSize - 8, $blockSize + 8));
            if ($block->u64(0) !== $blockSize) {
                throw new UnexpectedValueException('Signing block size fields differ');
            }

            $pairs = [];
            $offset = 8;
            $end = $block->length - 24;
            while ($offset < $end) {
                $length = $block->u64($offset);
                if ($length < 4 || $length > $end - $offset - 8) {
                    throw new UnexpectedValueException('Invalid signing block pair length');
                }
                $id = $block->u32($offset + 8);
                $pairs[$id] ??= $block->bytes($offset + 12, $length - 4);
                $offset += 8 + $length;
            }

            return $pairs;
        } finally {
            fclose($handle);
        }
    }

    /** @param resource $handle */
    private function centralDirectoryOffset($handle, int $fileSize): ?int
    {
        if ($fileSize < self::EOCD_MIN_SIZE) {
            return null;
        }
        $tailSize = min($fileSize, self::EOCD_MIN_SIZE + self::MAX_COMMENT_SIZE);
        $tailStart = $fileSize - $tailSize;
        $tail = new BinaryReader($this->readAt($handle, $tailStart, $tailSize));

        for ($pos = $tail->length - self::EOCD_MIN_SIZE; $pos >= 0; $pos--) {
            if ($tail->bytes($pos, 4) !== self::EOCD_SIGNATURE) {
                continue;
            }
            if ($pos + self::EOCD_MIN_SIZE + $tail->u16($pos + 20) !== $tail->length) {
                continue;
            }

            $offset = $tail->u32($pos + 16);
            if ($offset !== 0xFFFFFFFF) {
                return $offset;
            }

            return $this->zip64CentralDirectoryOffset($handle, $tailStart + $pos);
        }

        return null;
    }

    /** @param resource $handle */
    private function zip64CentralDirectoryOffset($handle, int $eocdOffset): ?int
    {
        if ($eocdOffset < 20) {
            return null;
        }
        $locator = new BinaryReader($this->readAt($handle, $eocdOffset - 20, 20));
        if ($locator->bytes(0, 4) !== "PK\x06\x07") {
            return null;
        }
        $record = new BinaryReader($this->readAt($handle, $locator->u64(8), 56));
        if ($record->bytes(0, 4) !== "PK\x06\x06") {
            return null;
        }

        return $record->u64(48);
    }

    /**
     * v2 and v3 share the layout: signers > signer > signed data > (digests, certificates).
     * The first signer is used; for v3 key rotation that signer's certificate is the newest key.
     */
    private function firstCertificate(string $value): string
    {
        $r = new BinaryReader($value);
        [$signers, $signersEnd] = $this->lengthPrefixed($r, 0, $r->length);
        if ($signers >= $signersEnd) {
            throw new UnexpectedValueException('Signature scheme block has no signers');
        }
        [$signer, $signerEnd] = $this->lengthPrefixed($r, $signers, $signersEnd);
        [$signedData, $signedDataEnd] = $this->lengthPrefixed($r, $signer, $signerEnd);
        [, $digestsEnd] = $this->lengthPrefixed($r, $signedData, $signedDataEnd);
        [$certificates, $certificatesEnd] = $this->lengthPrefixed($r, $digestsEnd, $signedDataEnd);
        if ($certificates >= $certificatesEnd) {
            throw new UnexpectedValueException('Signer has no certificates');
        }
        [$certificate, $certificateEnd] = $this->lengthPrefixed($r, $certificates, $certificatesEnd);
        if ($certificateEnd === $certificate) {
            throw new UnexpectedValueException('Empty certificate');
        }

        return $r->bytes($certificate, $certificateEnd - $certificate);
    }

    /** @return array{int, int} start and end of the content following the uint32 length prefix */
    private function lengthPrefixed(BinaryReader $r, int $offset, int $limit): array
    {
        $length = $r->u32($offset);
        $start = $offset + 4;
        if ($start + $length > $limit) {
            throw new UnexpectedValueException('Length-prefixed field exceeds its container');
        }

        return [$start, $start + $length];
    }

    /**
     * JAR (v1) signature: the first META-INF/*.RSA|DSA|EC file by name, and within its PKCS#7
     * the leaf certificate (one that does not issue another certificate in the bundle).
     */
    private function jarSignerCertificate(ZipArchive $zip): string
    {
        $signatureFiles = [];
        for ($i = 0; $i < $zip->numFiles; $i++) {
            $name = $zip->getNameIndex($i);
            if ($name !== false && preg_match('#^META-INF/[^/]+\.(RSA|DSA|EC)$#i', $name)) {
                $signatureFiles[] = $name;
            }
        }
        if ($signatureFiles === []) {
            throw new ApkInspectionException('APK 未签名（未找到 v1/v2/v3 签名），无法发布。');
        }
        sort($signatureFiles, SORT_STRING);

        if (! function_exists('openssl_pkcs7_read')) {
            throw new ApkInspectionException('服务器 PHP 缺少 openssl 扩展，无法读取 v1 签名证书。');
        }

        $pkcs7 = $zip->getFromName($signatureFiles[0]);
        $pem = "-----BEGIN PKCS7-----\n".chunk_split(base64_encode((string) $pkcs7), 64, "\n")."-----END PKCS7-----\n";
        $certificates = [];
        if ($pkcs7 === false || $pkcs7 === '' || ! openssl_pkcs7_read($pem, $certificates) || $certificates === []) {
            while (openssl_error_string() !== false) {
                // Drain the OpenSSL error queue so later calls do not report stale errors.
            }
            throw new ApkInspectionException("v1 签名文件 {$signatureFiles[0]} 无法解析或不含证书。");
        }

        return $this->pemToDer($this->leafCertificate($certificates));
    }

    /** @param list<string> $certificates PEM certificates */
    private function leafCertificate(array $certificates): string
    {
        if (count($certificates) === 1) {
            return $certificates[0];
        }

        $parsed = array_map(fn (string $pem) => openssl_x509_parse($pem) ?: [], $certificates);
        foreach ($parsed as $i => $candidate) {
            $issuesAnother = false;
            foreach ($parsed as $j => $other) {
                if ($i !== $j && ($other['issuer'] ?? null) === ($candidate['subject'] ?? false)) {
                    $issuesAnother = true;
                    break;
                }
            }
            if (! $issuesAnother) {
                return $certificates[$i];
            }
        }

        return $certificates[0];
    }

    private function pemToDer(string $pem): string
    {
        $base64 = preg_replace('#-----(BEGIN|END) CERTIFICATE-----|\s+#', '', $pem);
        $der = base64_decode((string) $base64, true);
        if ($der === false || $der === '') {
            throw new ApkInspectionException('v1 签名证书格式无法识别。');
        }

        return $der;
    }

    /** @param resource $handle */
    private function readAt($handle, int $offset, int $length): string
    {
        if ($offset < 0 || fseek($handle, $offset) !== 0) {
            throw new UnexpectedValueException("Unable to seek to {$offset}");
        }
        $data = '';
        while (strlen($data) < $length) {
            $chunk = fread($handle, $length - strlen($data));
            if ($chunk === false || $chunk === '') {
                throw new UnexpectedValueException("Unexpected end of file at {$offset}");
            }
            $data .= $chunk;
        }

        return $data;
    }
}
