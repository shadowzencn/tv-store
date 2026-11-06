<?php

namespace App\Support\Apk;

use UnexpectedValueException;

/**
 * Bounds-checked little-endian reads over an in-memory byte string.
 *
 * @internal
 */
final class BinaryReader
{
    public readonly int $length;

    public function __construct(public readonly string $data)
    {
        $this->length = strlen($data);
    }

    public function u8(int $offset): int
    {
        $this->require($offset, 1);

        return ord($this->data[$offset]);
    }

    public function u16(int $offset): int
    {
        $this->require($offset, 2);

        return unpack('v', $this->data, $offset)[1];
    }

    public function u32(int $offset): int
    {
        $this->require($offset, 4);

        return unpack('V', $this->data, $offset)[1];
    }

    public function u64(int $offset): int
    {
        $this->require($offset, 8);
        $value = unpack('P', $this->data, $offset)[1];
        if ($value < 0) {
            throw new UnexpectedValueException("64-bit value at {$offset} exceeds the supported range");
        }

        return $value;
    }

    public function bytes(int $offset, int $length): string
    {
        $this->require($offset, $length);

        return substr($this->data, $offset, $length);
    }

    public function require(int $offset, int $length): void
    {
        if ($offset < 0 || $length < 0 || $offset + $length > $this->length) {
            throw new UnexpectedValueException("Read of {$length} bytes at {$offset} is out of bounds ({$this->length})");
        }
    }

    public static function toSigned32(int $value): int
    {
        return $value >= 0x80000000 ? $value - 0x100000000 : $value;
    }
}
