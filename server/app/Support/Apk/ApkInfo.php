<?php

namespace App\Support\Apk;

final readonly class ApkInfo
{
    /**
     * @param  list<string>  $abis  ABIs found under lib/; empty when the APK has no native libraries
     */
    public function __construct(
        public string $packageName,
        public string $versionName,
        public int $versionCode,
        public int $minSdk,
        public ?int $targetSdk,
        public array $abis,
        public int $sizeBytes,
        public string $sha256,
        public string $signerSha256,
        public ?string $label,
    ) {}
}
