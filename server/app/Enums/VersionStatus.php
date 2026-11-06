<?php

namespace App\Enums;

use Filament\Support\Contracts\HasLabel;

enum VersionStatus: string implements HasLabel
{
    case Draft = 'draft';
    case Published = 'published';
    // A version that was published and then replaced by a newer published version; its file is kept.
    case Superseded = 'superseded';
    case Withdrawn = 'withdrawn';

    public function getLabel(): string
    {
        return match ($this) {
            self::Draft => '草稿',
            self::Published => '已发布',
            self::Superseded => '历史版本',
            self::Withdrawn => '已撤回',
        };
    }
}
