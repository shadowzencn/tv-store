<?php

namespace Tests\Unit\Apk;

use App\Support\Apk\ApkIcon;
use PHPUnit\Framework\TestCase;
use ZipArchive;

class ApkIconTest extends TestCase
{
    private const PNG = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=';

    private string $path;

    protected function setUp(): void
    {
        parent::setUp();
        $this->path = sys_get_temp_dir().DIRECTORY_SEPARATOR.'apk-icon-'.bin2hex(random_bytes(6)).'.zip';
    }

    protected function tearDown(): void
    {
        if (is_file($this->path)) {
            unlink($this->path);
        }
        parent::tearDown();
    }

    public function test_manifest_image_is_preferred_over_a_denser_bitmap(): void
    {
        $png = base64_decode(self::PNG);
        $zip = new ZipArchive;
        $zip->open($this->path, ZipArchive::CREATE);
        $zip->addFromString('res/mipmap-xxxhdpi-v4/ic_launcher.png', $png.'xxx');
        $zip->addFromString('res/mipmap-mdpi-v4/ic_launcher.png', $png);
        $zip->close();

        $zip->open($this->path);
        $icon = ApkIcon::extract($zip, 'res/mipmap-mdpi-v4/ic_launcher.png');
        $zip->close();

        $this->assertSame($png, $icon['bytes']);
        $this->assertSame('png', $icon['extension']);
    }

    public function test_xml_manifest_icon_falls_back_to_the_densest_launcher_bitmap(): void
    {
        $png = base64_decode(self::PNG);
        $zip = new ZipArchive;
        $zip->open($this->path, ZipArchive::CREATE);
        $zip->addFromString('res/mipmap-anydpi-v26/ic_launcher.xml', '<adaptive-icon/>');
        $zip->addFromString('res/mipmap-hdpi-v4/ic_launcher.png', $png.'hdpi');
        $zip->addFromString('res/mipmap-xxhdpi-v4/ic_launcher.webp', 'not-an-image');
        $zip->addFromString('res/drawable-xxhdpi-v4/ic_launcher.png', $png.'drawable');
        $zip->close();

        $zip->open($this->path);
        $icon = ApkIcon::extract($zip, 'res/mipmap-anydpi-v26/ic_launcher.xml');
        $zip->close();

        $this->assertSame($png.'drawable', $icon['bytes']);
        $this->assertSame('png', $icon['extension']);
    }

    public function test_missing_bitmap_returns_null(): void
    {
        $zip = new ZipArchive;
        $zip->open($this->path, ZipArchive::CREATE);
        $zip->addFromString('res/mipmap-anydpi-v26/ic_launcher.xml', '<adaptive-icon/>');
        $zip->close();

        $zip->open($this->path);
        $icon = ApkIcon::extract($zip, 'res/mipmap-anydpi-v26/ic_launcher.xml');
        $zip->close();

        $this->assertNull($icon);
    }
}
