<?php

namespace App\Enums;

use Filament\Support\Contracts\HasLabel;

enum Compatibility: string implements HasLabel
{
    case Verified = 'verified';
    case SpecMatch = 'spec_match';
    case Unverified = 'unverified';

    public function getLabel(): string
    {
        return match ($this) {
            self::Verified => '已实测',
            self::SpecMatch => '参数匹配',
            self::Unverified => '尚未验证',
        };
    }
}
