<?php

namespace App\Support\Apk;

interface ApkInspector
{
    /**
     * Reads package metadata and the signing certificate from an APK file without
     * external tools.
     *
     * @throws ApkInspectionException when the file is not a valid, signed APK
     */
    public function inspect(string $path): ApkInfo;
}
