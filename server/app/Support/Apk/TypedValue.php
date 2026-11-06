<?php

namespace App\Support\Apk;

/**
 * Res_value data types used by binary XML and resources.arsc.
 *
 * @internal
 */
final class TypedValue
{
    public const TYPE_NULL = 0x00;

    public const TYPE_REFERENCE = 0x01;

    public const TYPE_STRING = 0x03;

    public const TYPE_DYNAMIC_REFERENCE = 0x07;

    public const TYPE_INT_DEC = 0x10;

    public const TYPE_INT_HEX = 0x11;

    public const TYPE_INT_BOOLEAN = 0x12;

    public static function isReference(int $type): bool
    {
        return $type === self::TYPE_REFERENCE || $type === self::TYPE_DYNAMIC_REFERENCE;
    }

    public static function isInteger(int $type): bool
    {
        return $type >= self::TYPE_INT_DEC && $type <= 0x1F;
    }
}
