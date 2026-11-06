<?php

namespace Tests\Support;

use App\Support\Apk\ApkInfo;
use App\Support\Apk\ApkInspectionException;
use App\Support\Apk\ApkInspector;

/** Returns a preset result instead of parsing the uploaded file. */
class FakeApkInspector implements ApkInspector
{
    public ?ApkInfo $info = null;

    public ?string $failure = null;

    /** @var list<string> */
    public array $inspectedPaths = [];

    public function inspect(string $path): ApkInfo
    {
        $this->inspectedPaths[] = $path;

        if ($this->failure !== null) {
            throw new ApkInspectionException($this->failure);
        }

        return $this->info ?? throw new ApkInspectionException('测试未设置识别结果。');
    }

    public static function info(array $overrides = []): ApkInfo
    {
        return new ApkInfo(...array_merge([
            'packageName' => 'com.example.demo',
            'versionName' => '1.0.0',
            'versionCode' => 1,
            'minSdk' => 21,
            'targetSdk' => 30,
            'abis' => ['armeabi-v7a'],
            'sizeBytes' => 1024,
            'sha256' => str_repeat('a', 64),
            'signerSha256' => str_repeat('1', 64),
            'label' => 'Demo',
        ], $overrides));
    }
}
