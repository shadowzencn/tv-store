<?php

namespace App\Support\Apk;

use ZipArchive;

/**
 * Picks a raster launcher icon out of an APK. Adaptive-icon XML is skipped;
 * a png/webp/jpeg in mipmap or drawable is used instead.
 *
 * @internal
 */
final class ApkIcon
{
    public const MAX_BYTES = 1024 * 1024;

    /**
     * @return array{bytes: string, extension: string}|null
     */
    public static function extract(ZipArchive $zip, ?string $manifestPath): ?array
    {
        $fromManifest = self::imageFromEntry($zip, $manifestPath);
        if ($fromManifest !== null) {
            return $fromManifest;
        }

        $candidates = [];
        for ($i = 0; $i < $zip->numFiles; $i++) {
            $name = $zip->getNameIndex($i);
            if ($name === false) {
                continue;
            }
            $score = self::score($name);
            if ($score !== null) {
                $candidates[] = [$score, $name];
            }
        }
        usort($candidates, fn (array $a, array $b): int => $b[0] <=> $a[0]);

        foreach ($candidates as [, $name]) {
            $image = self::imageFromEntry($zip, $name);
            if ($image !== null) {
                return $image;
            }
        }

        return null;
    }

    /** @return array{bytes: string, extension: string}|null */
    private static function imageFromEntry(ZipArchive $zip, ?string $name): ?array
    {
        if ($name === null || $name === '' || str_contains($name, '..')) {
            return null;
        }

        $bytes = $zip->getFromName($name);
        if (! is_string($bytes) || $bytes === '' || strlen($bytes) > self::MAX_BYTES) {
            return null;
        }

        $extension = self::extensionOf($bytes);

        return $extension === null ? null : ['bytes' => $bytes, 'extension' => $extension];
    }

    /**
     * Higher is better. Names that are not a launcher bitmap score null.
     */
    private static function score(string $name): ?int
    {
        if (! preg_match('#^res/(mipmap|drawable)(?:-([a-z0-9]+))?(?:-v\d+)?/(ic_launcher(?:_round|_foreground)?|banner)\.(png|webp|jpg|jpeg)$#i', $name, $match)) {
            return null;
        }

        $density = match (strtolower($match[2] ?? '')) {
            'xxxhdpi' => 640,
            'xxhdpi' => 480,
            'xhdpi' => 320,
            'hdpi', 'tvdpi' => 240,
            'mdpi', '' => 160,
            'ldpi' => 120,
            'nodpi' => 400,
            default => 0,
        };
        if ($density === 0) {
            return null;
        }

        $kind = match (strtolower($match[3])) {
            'ic_launcher' => 40,
            'ic_launcher_round' => 30,
            'banner' => 20,
            'ic_launcher_foreground' => 10,
            default => 0,
        };
        $folder = strtolower($match[1]) === 'mipmap' ? 5 : 0;

        return $density + $kind + $folder;
    }

    private static function extensionOf(string $bytes): ?string
    {
        if (str_starts_with($bytes, "\x89PNG\r\n\x1a\n")) {
            return 'png';
        }
        if (str_starts_with($bytes, "\xFF\xD8\xFF")) {
            return 'jpg';
        }
        if (strlen($bytes) >= 12 && str_starts_with($bytes, 'RIFF') && substr($bytes, 8, 4) === 'WEBP') {
            return 'webp';
        }

        return null;
    }
}
