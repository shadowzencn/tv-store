<?php

namespace Tests\Unit\Apk;

use App\Support\Apk\ApkInfo;
use App\Support\Apk\ApkInspectionException;
use App\Support\Apk\ApkInspector;
use App\Support\Apk\PhpApkInspector;
use PHPUnit\Framework\Attributes\DataProvider;
use Tests\TestCase;
use ZipArchive;

/**
 * Expected values come from aapt2 dump badging, aapt2 dump xmltree and
 * apksigner verify --print-certs (build-tools 36.0.0) run against each fixture.
 *
 * Fixtures in tests/Fixtures/apk:
 * - installtest-v1/v2/v2other: android/installtest release outputs (v2 signed; v2other uses another key).
 * - tvstore-internal: android/app internal build (v2, four native ABIs, label from resources.arsc).
 * - installtest-jar-signed: installtest v1 re-signed with JAR (v1) signing only by a throwaway test key.
 * - installtest-v3-rotated: installtest v1 re-signed with v2 + v3 and key rotation old -> new (v3 carries the new key).
 * - minimal-*: aapt2-linked resource-only APKs (versionCodeMajor, versionName reference, split, unsigned).
 */
class PhpApkInspectorTest extends TestCase
{
    private const DEBUG_SIGNER = 'dd2cd9ef9d5db59f3428d288d1ba5542d461b90fa59dd79e8c6d7ae2587bb563';

    private const OTHER_SIGNER = '634866eb26ba7a76d2777a5a89073dfbf22c09654f3ce87a436f979f8086f0de';

    private const FIXTURE_OLD_SIGNER = '3cb48f567b4616bc76433a711c14ecc220fd0d494ee5af419e987430af32d26d';

    private const FIXTURE_NEW_SIGNER = 'b2611d266ea9b91848d5d218ec3a9af5ebb2767caac7d2154c26608ca650fbec';

    private string $tempDir;

    protected function setUp(): void
    {
        parent::setUp();
        $this->tempDir = sys_get_temp_dir().DIRECTORY_SEPARATOR.'apk-inspector-test-'.bin2hex(random_bytes(6));
        mkdir($this->tempDir);
    }

    protected function tearDown(): void
    {
        foreach (glob($this->tempDir.DIRECTORY_SEPARATOR.'*') ?: [] as $file) {
            unlink($file);
        }
        rmdir($this->tempDir);
        parent::tearDown();
    }

    public function test_container_binds_the_php_inspector(): void
    {
        $this->assertInstanceOf(PhpApkInspector::class, $this->app->make(ApkInspector::class));
    }

    /** @return array<string, array{string, array<string, mixed>}> */
    public static function fixtures(): array
    {
        $installTest = [
            'packageName' => 'com.shadowzen.tvstore.installtest',
            'minSdk' => 28,
            'targetSdk' => 36,
            'abis' => [],
            'label' => '安装测试',
        ];

        return [
            'v2 debug signed, version 1' => ['installtest-v1.apk', $installTest + [
                'versionName' => '1.0', 'versionCode' => 1, 'sizeBytes' => 23802,
                'sha256' => '11414bc265a22096a4c227cd5a6f15573231002e768d1b2e5137cbc98616586d',
                'signerSha256' => self::DEBUG_SIGNER,
            ]],
            'v2 debug signed, version 2' => ['installtest-v2.apk', $installTest + [
                'versionName' => '2.0', 'versionCode' => 2, 'sizeBytes' => 23802,
                'sha256' => '1649ee51a795f0258261d9ba2670916c0867223ae7515be3efc9398ffc076fd8',
                'signerSha256' => self::DEBUG_SIGNER,
            ]],
            'v2 other signer' => ['installtest-v2other.apk', $installTest + [
                'versionName' => '2.0', 'versionCode' => 2, 'sizeBytes' => 23842,
                'sha256' => '74ca261e2a51bc6a47ceedc4a960480910aafc4096d3112682f4b778c532d0f8',
                'signerSha256' => self::OTHER_SIGNER,
            ]],
            'v1 JAR signed only' => ['installtest-jar-signed.apk', $installTest + [
                'versionName' => '1.0', 'versionCode' => 1, 'sizeBytes' => 22796,
                'sha256' => '9c1943dd8b695af65dfb45f458d3d6a27c0055d71a544bebce45c5dec027c4a2',
                'signerSha256' => self::FIXTURE_OLD_SIGNER,
            ]],
            'v3 with key rotation uses the current signer' => ['installtest-v3-rotated.apk', $installTest + [
                'versionName' => '1.0', 'versionCode' => 1, 'sizeBytes' => 29870,
                'sha256' => 'af22d962e6b684c3860d6f226550da55548910fbbc9c70448c0811183f706a95',
                'signerSha256' => self::FIXTURE_NEW_SIGNER,
            ]],
            'store app with native libraries' => ['tvstore-internal.apk', [
                'packageName' => 'com.shadowzen.tvstore', 'versionName' => '0.1.0', 'versionCode' => 1,
                'minSdk' => 28, 'targetSdk' => 36, 'abis' => ['arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64'],
                'sizeBytes' => 2371781,
                'sha256' => '32b1dcfb868262eb44c24c578f56919f9febc62bea754bc53ea54d2fb7e16b10',
                'signerSha256' => self::DEBUG_SIGNER, 'label' => 'TV Store',
            ]],
            'versionCodeMajor, referenced versionName, no uses-sdk' => ['minimal-version-major.apk', [
                'packageName' => 'com.shadowzen.fixture.major', 'versionName' => '3.1-major',
                'versionCode' => (2 << 32) | 5, 'minSdk' => 1, 'targetSdk' => null, 'abis' => [],
                'sizeBytes' => 8524,
                'sha256' => 'b8bce2be4a5b1d61043c4a16fb869187e809eefa1941cf820181078e5f13ce67',
                'signerSha256' => self::FIXTURE_OLD_SIGNER, 'label' => '直接文字标签',
            ]],
        ];
    }

    /** @param array<string, mixed> $expected */
    #[DataProvider('fixtures')]
    public function test_reads_fixture_metadata(string $fixture, array $expected): void
    {
        $info = $this->inspect($this->fixture($fixture));

        $this->assertCount(10, $expected);
        foreach ($expected as $field => $value) {
            $this->assertSame($value, $info->{$field}, "Field {$field} of {$fixture}");
        }
    }

    public function test_same_key_gives_same_signer_and_different_key_differs(): void
    {
        $v1 = $this->inspect($this->fixture('installtest-v1.apk'));
        $v2 = $this->inspect($this->fixture('installtest-v2.apk'));
        $other = $this->inspect($this->fixture('installtest-v2other.apk'));

        $this->assertSame($v1->signerSha256, $v2->signerSha256);
        $this->assertNotSame($v2->signerSha256, $other->signerSha256);
        $this->assertNotSame($v1->sha256, $v2->sha256);
    }

    public function test_reads_utf8_string_pool_manifest(): void
    {
        $manifest = $this->utf8Manifest();
        $path = $this->zip('utf8.apk', [
            'AndroidManifest.xml' => $manifest,
            'META-INF/CERT.RSA' => $this->jarSignatureBlock(),
            'lib/armeabi-v7a/libdemo.so' => 'x',
        ]);

        $info = $this->inspect($path);

        $this->assertSame('com.example.utf8', $info->packageName);
        $this->assertSame('版本一', $info->versionName);
        $this->assertSame(7, $info->versionCode);
        $this->assertSame(21, $info->minSdk);
        $this->assertNull($info->targetSdk);
        $this->assertSame('中文标签', $info->label);
        $this->assertSame(['armeabi-v7a'], $info->abis);
        $this->assertSame(self::FIXTURE_OLD_SIGNER, $info->signerSha256);
    }

    public function test_rejects_missing_file(): void
    {
        $this->assertRejected($this->tempDir.DIRECTORY_SEPARATOR.'missing.apk', '不存在');
    }

    public function test_rejects_non_zip_file(): void
    {
        $path = $this->tempDir.DIRECTORY_SEPARATOR.'text.apk';
        file_put_contents($path, 'this is not an apk');

        $this->assertRejected($path, 'ZIP');
    }

    public function test_rejects_random_bytes(): void
    {
        $path = $this->tempDir.DIRECTORY_SEPARATOR.'random.apk';
        file_put_contents($path, random_bytes(4096));

        $this->assertRejected($path, 'ZIP');
    }

    public function test_rejects_truncated_zip(): void
    {
        $path = $this->tempDir.DIRECTORY_SEPARATOR.'truncated.apk';
        file_put_contents($path, substr((string) file_get_contents($this->fixture('installtest-v1.apk')), 0, 4000));

        $this->assertRejected($path, 'ZIP');
    }

    public function test_rejects_zip_without_manifest(): void
    {
        $entries = $this->fixtureEntries('installtest-v2.apk');
        unset($entries['AndroidManifest.xml']);

        $this->assertRejected($this->zip('no-manifest.apk', $entries), 'AndroidManifest.xml');
    }

    public function test_rejects_corrupted_manifest(): void
    {
        $entries = $this->fixtureEntries('installtest-jar-signed.apk');
        $entries['AndroidManifest.xml'] = random_bytes(512);

        $this->assertRejected($this->zip('garbage-manifest.apk', $entries), '损坏');
    }

    public function test_rejects_truncated_manifest(): void
    {
        $entries = $this->fixtureEntries('installtest-jar-signed.apk');
        $manifest = $entries['AndroidManifest.xml'];
        $entries['AndroidManifest.xml'] = substr($manifest, 0, intdiv(strlen($manifest), 2));

        $this->assertRejected($this->zip('truncated-manifest.apk', $entries), '损坏');
    }

    public function test_rejects_unsigned_apk(): void
    {
        $this->assertRejected($this->fixture('minimal-unsigned.apk'), '未签名');
    }

    public function test_rejects_v2_apk_rebuilt_without_signatures(): void
    {
        // Rewriting the entries into a fresh zip drops the APK Signing Block.
        $this->assertRejected($this->zip('stripped.apk', $this->fixtureEntries('installtest-v2.apk')), '未签名');
    }

    public function test_rejects_split_apk(): void
    {
        $this->assertRejected($this->fixture('minimal-split.apk'), '拆分');
    }

    private function inspect(string $path): ApkInfo
    {
        return (new PhpApkInspector)->inspect($path);
    }

    private function assertRejected(string $path, string $messageFragment): void
    {
        try {
            $this->inspect($path);
        } catch (ApkInspectionException $e) {
            $this->assertStringContainsString($messageFragment, $e->getMessage());

            return;
        }
        $this->fail("Expected ApkInspectionException for {$path}");
    }

    private function fixture(string $name): string
    {
        return base_path('tests/Fixtures/apk/'.$name);
    }

    /** @return array<string, string> */
    private function fixtureEntries(string $name): array
    {
        $zip = new ZipArchive;
        $this->assertTrue($zip->open($this->fixture($name), ZipArchive::RDONLY));
        $entries = [];
        for ($i = 0; $i < $zip->numFiles; $i++) {
            $entries[(string) $zip->getNameIndex($i)] = (string) $zip->getFromIndex($i);
        }
        $zip->close();

        return $entries;
    }

    /** @param array<string, string> $entries */
    private function zip(string $name, array $entries): string
    {
        $path = $this->tempDir.DIRECTORY_SEPARATOR.$name;
        $zip = new ZipArchive;
        $this->assertTrue($zip->open($path, ZipArchive::CREATE | ZipArchive::OVERWRITE));
        foreach ($entries as $entry => $content) {
            $zip->addFromString($entry, $content);
        }
        $this->assertTrue($zip->close());

        return $path;
    }

    private function jarSignatureBlock(): string
    {
        foreach ($this->fixtureEntries('installtest-jar-signed.apk') as $name => $content) {
            if (preg_match('#^META-INF/[^/]+\.RSA$#', $name)) {
                return $content;
            }
        }
        $this->fail('JAR signature block not found in fixture');
    }

    /**
     * Builds a binary XML manifest with a UTF-8 string pool, which aapt2 does not emit for
     * manifests but older toolchains and repackagers do.
     */
    private function utf8Manifest(): string
    {
        $strings = ['versionCode', 'versionName', 'minSdkVersion', 'label',
            'http://schemas.android.com/apk/res/android', 'package', 'manifest', 'uses-sdk',
            'application', 'com.example.utf8', '版本一', '中文标签', 'android'];
        $resourceIds = [0x0101021B, 0x0101021C, 0x0101020C, 0x01010001];
        $ns = 4;
        $string = 0x03;
        $intDec = 0x10;

        $pool = '';
        $offsets = '';
        foreach ($strings as $value) {
            $offsets .= pack('V', strlen($pool));
            $pool .= chr(mb_strlen($value, 'UTF-8')).chr(strlen($value)).$value."\0";
        }
        $pool = str_pad($pool, (int) ceil(strlen($pool) / 4) * 4, "\0");
        $stringPool = pack('vvVVVVVV', 0x0001, 28, 28 + strlen($offsets) + strlen($pool), count($strings), 0, 0x100, 28 + strlen($offsets), 0).$offsets.$pool;

        $resourceMap = pack('vvV', 0x0180, 8, 8 + 4 * count($resourceIds)).pack('V*', ...$resourceIds);

        $attribute = fn (int $attrNs, int $name, int $raw, int $type, int $data) => pack('VVVvCCV', $attrNs, $name, $raw, 8, 0, $type, $data);
        $start = function (int $name, array $attributes): string {
            $body = pack('VVvvvvvv', 0xFFFFFFFF, $name, 20, 20, count($attributes), 0, 0, 0).implode('', $attributes);

            return pack('vvVVV', 0x0102, 16, 16 + strlen($body), 1, 0xFFFFFFFF).$body;
        };
        $end = fn (int $name) => pack('vvVVVVV', 0x0103, 16, 24, 1, 0xFFFFFFFF, 0xFFFFFFFF, $name);
        $none = 0xFFFFFFFF;

        $body = $stringPool.$resourceMap
            .pack('vvVVVVV', 0x0100, 16, 24, 1, 0xFFFFFFFF, 12, $ns)
            .$start(6, [
                $attribute($ns, 0, $none, $intDec, 7),
                $attribute($ns, 1, 10, $string, 10),
                $attribute($none, 5, 9, $string, 9),
            ])
            .$start(7, [$attribute($ns, 2, $none, $intDec, 21)]).$end(7)
            .$start(8, [$attribute($ns, 3, 11, $string, 11)]).$end(8)
            .$end(6)
            .pack('vvVVVVV', 0x0101, 16, 24, 1, 0xFFFFFFFF, 12, $ns);

        return pack('vvV', 0x0003, 8, 8 + strlen($body)).$body;
    }
}
