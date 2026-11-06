<?php

namespace App\Support\Apk;

use UnexpectedValueException;

/**
 * Looks up simple (non-bag) values in resources.arsc, preferring the default configuration.
 *
 * @internal
 */
final class ResourceTable
{
    private const RES_TABLE_TYPE = 0x0002;

    private const RES_STRING_POOL_TYPE = 0x0001;

    private const RES_TABLE_PACKAGE_TYPE = 0x0200;

    private const RES_TABLE_TYPE_TYPE = 0x0201;

    private const NO_ENTRY = 0xFFFFFFFF;

    private const TYPE_FLAG_SPARSE = 0x01;

    private const TYPE_FLAG_OFFSET16 = 0x02;

    private const ENTRY_FLAG_COMPLEX = 0x0001;

    private const ENTRY_FLAG_COMPACT = 0x0008;

    private const MAX_REFERENCE_DEPTH = 8;

    private readonly BinaryReader $reader;

    private ?StringPool $strings = null;

    /** @var array<int, array{start: int, headerSize: int, end: int}> */
    private array $packages = [];

    /** @throws UnexpectedValueException when the table is malformed */
    public function __construct(string $data)
    {
        $this->reader = new BinaryReader($data);
        if ($this->reader->u16(0) !== self::RES_TABLE_TYPE) {
            throw new UnexpectedValueException('Not a resource table');
        }

        $end = min($this->reader->u32(4), $this->reader->length);
        $offset = $this->reader->u16(2);
        while ($offset + 8 <= $end) {
            [$type, $headerSize, $size] = $this->chunkHeader($offset, $end);
            if ($type === self::RES_STRING_POOL_TYPE) {
                $this->strings ??= new StringPool($this->reader, $offset);
            } elseif ($type === self::RES_TABLE_PACKAGE_TYPE) {
                $this->packages[$this->reader->u32($offset + 8)] ??= ['start' => $offset, 'headerSize' => $headerSize, 'end' => $offset + $size];
            }
            $offset += $size;
        }
    }

    /**
     * Follows references until a non-reference value is found.
     *
     * @return array{type: int, data: int, string: ?string}|null null when the resource is missing or is a bag
     */
    public function resolve(int $resourceId): ?array
    {
        for ($depth = 0; $depth < self::MAX_REFERENCE_DEPTH; $depth++) {
            $value = $this->find($resourceId);
            if ($value === null) {
                return null;
            }
            if (! TypedValue::isReference($value['type'])) {
                $value['string'] = $value['type'] === TypedValue::TYPE_STRING ? $this->strings?->get($value['data']) : null;

                return $value;
            }
            $resourceId = $value['data'];
        }

        return null;
    }

    /** @return array{type: int, data: int}|null */
    private function find(int $resourceId): ?array
    {
        $package = $this->packages[($resourceId >> 24) & 0xFF] ?? null;
        if ($package === null) {
            return null;
        }
        $typeId = ($resourceId >> 16) & 0xFF;
        $entryIndex = $resourceId & 0xFFFF;

        $fallback = null;
        $offset = $package['start'] + $package['headerSize'];
        while ($offset + 8 <= $package['end']) {
            [$type, $headerSize, $size] = $this->chunkHeader($offset, $package['end']);
            if ($type === self::RES_TABLE_TYPE_TYPE && $this->reader->u8($offset + 8) === $typeId) {
                $value = $this->entryValue($offset, $headerSize, $size, $entryIndex);
                if ($value !== null) {
                    if ($this->isDefaultConfig($offset, $headerSize)) {
                        return $value;
                    }
                    $fallback ??= $value;
                }
            }
            $offset += $size;
        }

        return $fallback;
    }

    /** @return array{type: int, data: int}|null */
    private function entryValue(int $chunk, int $headerSize, int $size, int $index): ?array
    {
        $r = $this->reader;
        $flags = $r->u8($chunk + 9);
        $entryCount = $r->u32($chunk + 12);
        $entriesStart = $chunk + $r->u32($chunk + 16);
        $offsets = $chunk + $headerSize;

        $entryOffset = null;
        if ($flags & self::TYPE_FLAG_SPARSE) {
            for ($i = 0; $i < $entryCount; $i++) {
                if ($r->u16($offsets + $i * 4) === $index) {
                    $entryOffset = $r->u16($offsets + $i * 4 + 2) * 4;
                    break;
                }
            }
        } elseif ($index < $entryCount) {
            if ($flags & self::TYPE_FLAG_OFFSET16) {
                $raw = $r->u16($offsets + $index * 2);
                $entryOffset = $raw === 0xFFFF ? null : $raw * 4;
            } else {
                $raw = $r->u32($offsets + $index * 4);
                $entryOffset = $raw === self::NO_ENTRY ? null : $raw;
            }
        }
        if ($entryOffset === null) {
            return null;
        }

        $entry = $entriesStart + $entryOffset;
        if ($entry + 8 > $chunk + $size) {
            throw new UnexpectedValueException('Resource entry exceeds its type chunk');
        }
        $entryFlags = $r->u16($entry + 2);
        if ($entryFlags & self::ENTRY_FLAG_COMPACT) {
            return ['type' => $entryFlags >> 8, 'data' => $r->u32($entry + 4)];
        }
        if ($entryFlags & self::ENTRY_FLAG_COMPLEX) {
            return null;
        }
        $value = $entry + $r->u16($entry);

        return ['type' => $r->u8($value + 3), 'data' => $r->u32($value + 4)];
    }

    private function isDefaultConfig(int $chunk, int $headerSize): bool
    {
        $config = $chunk + 20;
        $configSize = min($this->reader->u32($config), $headerSize - 20);
        if ($configSize <= 4) {
            return true;
        }

        return trim($this->reader->bytes($config + 4, $configSize - 4), "\0") === '';
    }

    /** @return array{int, int, int} */
    private function chunkHeader(int $offset, int $end): array
    {
        $type = $this->reader->u16($offset);
        $headerSize = $this->reader->u16($offset + 2);
        $size = $this->reader->u32($offset + 4);
        if ($size < 8 || $headerSize < 8 || $headerSize > $size || $offset + $size > $end) {
            throw new UnexpectedValueException("Invalid resource chunk at {$offset}");
        }

        return [$type, $headerSize, $size];
    }
}
