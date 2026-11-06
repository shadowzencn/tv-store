<?php

namespace App\Support\Apk;

use UnexpectedValueException;

/**
 * Minimal parser for Android binary XML (AXML), enough to read element names and typed attribute values.
 *
 * @internal
 */
final class BinaryXmlParser
{
    public const ANDROID_NS = 'http://schemas.android.com/apk/res/android';

    private const RES_XML_TYPE = 0x0003;

    private const RES_STRING_POOL_TYPE = 0x0001;

    private const RES_XML_RESOURCE_MAP_TYPE = 0x0180;

    private const RES_XML_START_ELEMENT_TYPE = 0x0102;

    private const RES_XML_END_ELEMENT_TYPE = 0x0103;

    /**
     * @return list<array{name: string, depth: int, attributes: list<array{ns: ?string, name: string, resId: int, rawValue: ?string, type: int, data: int, string: ?string}>}>
     *
     * @throws UnexpectedValueException when the document is malformed
     */
    public function parse(string $data): array
    {
        $reader = new BinaryReader($data);
        if ($reader->u16(0) !== self::RES_XML_TYPE) {
            throw new UnexpectedValueException('Not a binary XML document');
        }
        $offset = $reader->u16(2);
        $end = min($reader->u32(4), $reader->length);

        $strings = null;
        $resourceIds = [];
        $elements = [];
        $depth = 0;

        while ($offset + 8 <= $end) {
            $type = $reader->u16($offset);
            $headerSize = $reader->u16($offset + 2);
            $size = $reader->u32($offset + 4);
            if ($size < 8 || $headerSize < 8 || $headerSize > $size || $offset + $size > $end) {
                throw new UnexpectedValueException("Invalid chunk at {$offset}");
            }

            switch ($type) {
                case self::RES_STRING_POOL_TYPE:
                    $strings ??= new StringPool($reader, $offset);
                    break;
                case self::RES_XML_RESOURCE_MAP_TYPE:
                    $count = intdiv($size - $headerSize, 4);
                    for ($i = 0; $i < $count; $i++) {
                        $resourceIds[$i] = $reader->u32($offset + $headerSize + $i * 4);
                    }
                    break;
                case self::RES_XML_START_ELEMENT_TYPE:
                    if ($strings === null) {
                        throw new UnexpectedValueException('Element appears before the string pool');
                    }
                    $elements[] = $this->readElement($reader, $strings, $resourceIds, $offset + $headerSize, $offset + $size, $depth);
                    $depth++;
                    break;
                case self::RES_XML_END_ELEMENT_TYPE:
                    $depth--;
                    break;
            }

            $offset += $size;
        }

        if ($elements === []) {
            throw new UnexpectedValueException('Document has no elements');
        }

        return $elements;
    }

    /**
     * @param  array<int, int>  $resourceIds
     * @return array{name: string, depth: int, attributes: list<array{ns: ?string, name: string, resId: int, rawValue: ?string, type: int, data: int, string: ?string}>}
     */
    private function readElement(BinaryReader $reader, StringPool $strings, array $resourceIds, int $ext, int $chunkEnd, int $depth): array
    {
        $name = $strings->get($reader->u32($ext + 4)) ?? '';
        $attributeStart = $reader->u16($ext + 8);
        $attributeSize = $reader->u16($ext + 10);
        $attributeCount = $reader->u16($ext + 12);
        if ($attributeCount > 0 && ($attributeSize < 20 || $ext + $attributeStart + $attributeCount * $attributeSize > $chunkEnd)) {
            throw new UnexpectedValueException("Invalid attribute layout in <{$name}>");
        }

        $attributes = [];
        for ($i = 0; $i < $attributeCount; $i++) {
            $a = $ext + $attributeStart + $i * $attributeSize;
            $nameIndex = $reader->u32($a + 4);
            $type = $reader->u8($a + 15);
            $data = $reader->u32($a + 16);
            $attributes[] = [
                'ns' => $strings->get($reader->u32($a)),
                'name' => $strings->get($nameIndex) ?? '',
                'resId' => $resourceIds[$nameIndex] ?? 0,
                'rawValue' => $strings->get($reader->u32($a + 8)),
                'type' => $type,
                'data' => $data,
                'string' => $type === TypedValue::TYPE_STRING ? $strings->get($data) : null,
            ];
        }

        return ['name' => $name, 'depth' => $depth, 'attributes' => $attributes];
    }
}
