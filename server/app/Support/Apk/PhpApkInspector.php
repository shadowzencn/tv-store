<?php

namespace App\Support\Apk;

use UnexpectedValueException;
use ValueError;
use ZipArchive;

/**
 * Pure PHP APK inspector: reads AndroidManifest.xml (binary XML), resources.arsc for
 * referenced strings, and the signing certificate, without aapt2, apksigner or Java.
 */
final class PhpApkInspector implements ApkInspector
{
    private const ATTR_LABEL = 0x01010001;

    private const ATTR_ICON = 0x01010002;

    private const ATTR_BANNER = 0x0101042F;

    private const ATTR_MIN_SDK_VERSION = 0x0101020C;

    private const ATTR_VERSION_CODE = 0x0101021B;

    private const ATTR_VERSION_NAME = 0x0101021C;

    private const ATTR_TARGET_SDK_VERSION = 0x01010270;

    private const ATTR_VERSION_CODE_MAJOR = 0x01010576;

    private ?ResourceTable $resources = null;

    private bool $resourcesLoaded = false;

    private ?ZipArchive $zip = null;

    public function __construct(
        private readonly BinaryXmlParser $xmlParser = new BinaryXmlParser,
        private readonly ApkSignatureReader $signatureReader = new ApkSignatureReader,
    ) {}

    public function inspect(string $path): ApkInfo
    {
        if (! is_file($path) || ! is_readable($path)) {
            throw new ApkInspectionException('APK 文件不存在或无法读取。');
        }
        $sizeBytes = filesize($path);
        if ($sizeBytes === false) {
            throw new ApkInspectionException('无法读取 APK 文件大小。');
        }
        if ($sizeBytes < 4 || file_get_contents($path, false, null, 0, 4) !== "PK\x03\x04") {
            throw new ApkInspectionException('文件不是有效的 APK（ZIP 格式）。');
        }

        $zip = new ZipArchive;
        try {
            $opened = $zip->open($path, ZipArchive::RDONLY);
        } catch (ValueError $e) {
            throw new ApkInspectionException('文件不是有效的 APK（ZIP 格式）。', previous: $e);
        }
        if ($opened !== true) {
            throw new ApkInspectionException("文件不是有效的 APK（ZIP 格式，错误码 {$opened}）。");
        }

        $this->zip = $zip;
        $this->resources = null;
        $this->resourcesLoaded = false;

        try {
            $manifest = $this->readManifest($zip);
            $signer = $this->signatureReader->signerCertificate($path, $sizeBytes, $zip);
            $abis = $this->abis($zip);
            $icon = ApkIcon::extract($zip, $manifest['iconPath'] ?? $manifest['bannerPath']);
        } finally {
            $this->zip = null;
            $this->resources = null;
            $zip->close();
        }

        $sha256 = hash_file('sha256', $path);
        if ($sha256 === false) {
            throw new ApkInspectionException('无法计算 APK 文件的 SHA-256。');
        }

        return new ApkInfo(
            packageName: $manifest['packageName'],
            versionName: $manifest['versionName'],
            versionCode: $manifest['versionCode'],
            minSdk: $manifest['minSdk'],
            targetSdk: $manifest['targetSdk'],
            abis: $abis,
            sizeBytes: $sizeBytes,
            sha256: $sha256,
            signerSha256: hash('sha256', $signer),
            label: $manifest['label'],
            iconBytes: $icon['bytes'] ?? null,
            iconExtension: $icon['extension'] ?? null,
        );
    }

    /** @return array{packageName: string, versionName: string, versionCode: int, minSdk: int, targetSdk: ?int, label: ?string, iconPath: ?string, bannerPath: ?string} */
    private function readManifest(ZipArchive $zip): array
    {
        $data = $zip->getFromName('AndroidManifest.xml');
        if ($data === false) {
            throw new ApkInspectionException('APK 中缺少 AndroidManifest.xml。');
        }

        try {
            $elements = $this->xmlParser->parse($data);
        } catch (UnexpectedValueException $e) {
            throw new ApkInspectionException('AndroidManifest.xml 已损坏或格式无法识别。', previous: $e);
        }

        $manifest = $elements[0];
        if ($manifest['name'] !== 'manifest' || $manifest['depth'] !== 0) {
            throw new ApkInspectionException('AndroidManifest.xml 的根元素不是 manifest。');
        }

        $split = $this->plainAttribute($manifest, 'split');
        if ($split !== null && $split !== '') {
            throw new ApkInspectionException("这是拆分 APK（split=\"{$split}\"），请上传完整的单个 APK。");
        }

        $packageName = $this->plainAttribute($manifest, 'package');
        if ($packageName === null || $packageName === '') {
            throw new ApkInspectionException('AndroidManifest.xml 缺少包名（package）。');
        }

        $versionCode = $this->integerAttribute($manifest, self::ATTR_VERSION_CODE, 'versionCode');
        if ($versionCode === null) {
            throw new ApkInspectionException('AndroidManifest.xml 缺少 versionCode 或其值无法识别。');
        }
        $versionCodeMajor = $this->integerAttribute($manifest, self::ATTR_VERSION_CODE_MAJOR, 'versionCodeMajor') ?? 0;
        if ($versionCodeMajor < 0 || $versionCodeMajor > 0x7FFFFFFF) {
            throw new ApkInspectionException('AndroidManifest.xml 的 versionCodeMajor 超出支持范围。');
        }

        $usesSdk = $this->child($elements, 'uses-sdk');
        $minSdk = $usesSdk === null ? null : $this->integerAttribute($usesSdk, self::ATTR_MIN_SDK_VERSION, 'minSdkVersion');
        $targetSdk = $usesSdk === null ? null : $this->integerAttribute($usesSdk, self::ATTR_TARGET_SDK_VERSION, 'targetSdkVersion');

        $application = $this->child($elements, 'application');

        return [
            'packageName' => $packageName,
            'versionName' => $this->stringAttribute($manifest, self::ATTR_VERSION_NAME, 'versionName') ?? '',
            // Same as PackageInfo.getLongVersionCode(): major in the high 32 bits, versionCode as unsigned low bits.
            'versionCode' => ($versionCodeMajor << 32) | ($versionCode & 0xFFFFFFFF),
            'minSdk' => $minSdk ?? 1,
            'targetSdk' => $targetSdk,
            'label' => $application === null ? null : $this->stringAttribute($application, self::ATTR_LABEL, 'label'),
            'iconPath' => $application === null ? null : $this->resourcePath($application, self::ATTR_ICON, 'icon'),
            'bannerPath' => $application === null ? null : $this->resourcePath($application, self::ATTR_BANNER, 'banner'),
        ];
    }

    /**
     * @param  list<array{name: string, depth: int, attributes: list<array<string, mixed>>}>  $elements
     * @return array{name: string, depth: int, attributes: list<array<string, mixed>>}|null
     */
    private function child(array $elements, string $name): ?array
    {
        foreach ($elements as $element) {
            if ($element['depth'] === 1 && $element['name'] === $name) {
                return $element;
            }
        }

        return null;
    }

    /**
     * Android identifies framework attributes by resource ID, so names are only a fallback
     * (obfuscated manifests can carry empty or renamed attribute names).
     *
     * @param  array{attributes: list<array<string, mixed>>}  $element
     * @return array{ns: ?string, name: string, resId: int, rawValue: ?string, type: int, data: int, string: ?string}|null
     */
    private function androidAttribute(array $element, int $resId, string $name): ?array
    {
        foreach ($element['attributes'] as $attribute) {
            if ($attribute['resId'] === $resId) {
                return $attribute;
            }
        }
        foreach ($element['attributes'] as $attribute) {
            if ($attribute['resId'] === 0 && $attribute['name'] === $name && $attribute['ns'] === BinaryXmlParser::ANDROID_NS) {
                return $attribute;
            }
        }

        return null;
    }

    /** @param array{attributes: list<array<string, mixed>>} $element */
    private function plainAttribute(array $element, string $name): ?string
    {
        foreach ($element['attributes'] as $attribute) {
            if ($attribute['name'] === $name && ($attribute['ns'] ?? '') === '') {
                return $attribute['string'] ?? $attribute['rawValue'];
            }
        }

        return null;
    }

    /** @param array{attributes: list<array<string, mixed>>} $element */
    private function integerAttribute(array $element, int $resId, string $name): ?int
    {
        $attribute = $this->androidAttribute($element, $resId, $name);
        if ($attribute === null) {
            return null;
        }
        $value = $this->resolveValue($attribute);
        if ($value === null) {
            return null;
        }
        if (TypedValue::isInteger($value['type'])) {
            return BinaryReader::toSigned32($value['data']);
        }
        if ($value['type'] === TypedValue::TYPE_STRING && preg_match('/^-?\d{1,10}$/', trim((string) $value['string']))) {
            return (int) trim((string) $value['string']);
        }

        return null;
    }

    /** @param array{attributes: list<array<string, mixed>>} $element */
    private function stringAttribute(array $element, int $resId, string $name): ?string
    {
        $attribute = $this->androidAttribute($element, $resId, $name);
        if ($attribute === null) {
            return null;
        }
        $value = $this->resolveValue($attribute);
        if ($value === null) {
            return null;
        }
        if ($value['type'] === TypedValue::TYPE_STRING) {
            return $value['string'];
        }
        if (TypedValue::isInteger($value['type']) && $value['type'] !== TypedValue::TYPE_INT_BOOLEAN) {
            return (string) BinaryReader::toSigned32($value['data']);
        }

        return null;
    }

    /** @param array{attributes: list<array<string, mixed>>} $element */
    private function resourcePath(array $element, int $resId, string $name): ?string
    {
        $attribute = $this->androidAttribute($element, $resId, $name);
        if ($attribute === null) {
            return null;
        }
        $value = $this->resolveValue($attribute);
        if ($value === null || $value['type'] !== TypedValue::TYPE_STRING) {
            return null;
        }
        $path = $value['string'];

        return is_string($path) && $path !== '' ? $path : null;
    }

    /**
     * @param  array{type: int, data: int, string: ?string}  $attribute
     * @return array{type: int, data: int, string: ?string}|null null for unresolvable references
     */
    private function resolveValue(array $attribute): ?array
    {
        if (! TypedValue::isReference($attribute['type'])) {
            return $attribute;
        }

        return $this->resources()?->resolve($attribute['data']);
    }

    /**
     * Missing or malformed resources.arsc only affects optional values (label, versionName), so
     * it degrades to "unresolved" instead of rejecting the APK.
     */
    private function resources(): ?ResourceTable
    {
        if (! $this->resourcesLoaded) {
            $this->resourcesLoaded = true;
            $data = $this->zip?->getFromName('resources.arsc');
            if (is_string($data)) {
                try {
                    $this->resources = new ResourceTable($data);
                } catch (UnexpectedValueException) {
                    $this->resources = null;
                }
            }
        }

        return $this->resources;
    }

    /** @return list<string> */
    private function abis(ZipArchive $zip): array
    {
        $abis = [];
        for ($i = 0; $i < $zip->numFiles; $i++) {
            $name = $zip->getNameIndex($i);
            if ($name !== false && preg_match('#^lib/([^/]+)/[^/]+$#', $name, $m)) {
                $abis[$m[1]] = true;
            }
        }
        $abis = array_keys($abis);
        sort($abis, SORT_STRING);

        return $abis;
    }
}
