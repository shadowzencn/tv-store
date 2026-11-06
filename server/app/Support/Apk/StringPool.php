<?php

namespace App\Support\Apk;

use UnexpectedValueException;

/**
 * ResStringPool chunk shared by binary XML and resources.arsc. Strings are decoded lazily.
 *
 * @internal
 */
final class StringPool
{
    private const FLAG_UTF8 = 0x100;

    private readonly int $count;

    private readonly bool $utf8;

    private readonly int $offsetsStart;

    private readonly int $stringsStart;

    private readonly int $chunkEnd;

    /** @var array<int, string> */
    private array $cache = [];

    public function __construct(private readonly BinaryReader $reader, int $chunkStart)
    {
        if ($reader->u16($chunkStart) !== 0x0001) {
            throw new UnexpectedValueException('Chunk is not a string pool');
        }
        $headerSize = $reader->u16($chunkStart + 2);
        $size = $reader->u32($chunkStart + 4);
        $reader->require($chunkStart, $size);

        $this->count = $reader->u32($chunkStart + 8);
        $flags = $reader->u32($chunkStart + 16);
        $this->utf8 = ($flags & self::FLAG_UTF8) !== 0;
        $this->offsetsStart = $chunkStart + $headerSize;
        $this->stringsStart = $chunkStart + $reader->u32($chunkStart + 20);
        $this->chunkEnd = $chunkStart + $size;

        if ($this->count > intdiv($size, 4) || $this->offsetsStart + $this->count * 4 > $this->chunkEnd) {
            throw new UnexpectedValueException('String pool offsets exceed the chunk');
        }
    }

    public function get(int $index): ?string
    {
        if ($index === 0xFFFFFFFF || $index < 0) {
            return null;
        }
        if ($index >= $this->count) {
            throw new UnexpectedValueException("String index {$index} is out of range");
        }

        return $this->cache[$index] ??= $this->decode($this->stringsStart + $this->reader->u32($this->offsetsStart + $index * 4));
    }

    private function decode(int $offset): string
    {
        if ($this->utf8) {
            // UTF-8 entries store the UTF-16 length first and then the byte length; only the latter is needed.
            [, $offset] = $this->utf8Length($offset);
            [$byteLength, $offset] = $this->utf8Length($offset);
            $this->checkEnd($offset, $byteLength);

            return mb_scrub($this->reader->bytes($offset, $byteLength), 'UTF-8');
        }

        $length = $this->reader->u16($offset);
        $offset += 2;
        if ($length & 0x8000) {
            $length = (($length & 0x7FFF) << 16) | $this->reader->u16($offset);
            $offset += 2;
        }
        $this->checkEnd($offset, $length * 2);

        return mb_convert_encoding($this->reader->bytes($offset, $length * 2), 'UTF-8', 'UTF-16LE');
    }

    /** @return array{int, int} */
    private function utf8Length(int $offset): array
    {
        $length = $this->reader->u8($offset++);
        if ($length & 0x80) {
            $length = (($length & 0x7F) << 8) | $this->reader->u8($offset++);
        }

        return [$length, $offset];
    }

    private function checkEnd(int $offset, int $length): void
    {
        if ($offset + $length > $this->chunkEnd) {
            throw new UnexpectedValueException('String data exceeds the string pool chunk');
        }
    }
}
