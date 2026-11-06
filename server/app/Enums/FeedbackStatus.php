<?php

namespace App\Enums;

use Filament\Support\Contracts\HasLabel;

enum FeedbackStatus: string implements HasLabel
{
    case Pending = 'pending';
    case Processing = 'processing';
    case Resolved = 'resolved';
    case Ignored = 'ignored';

    public function getLabel(): string
    {
        return match ($this) {
            self::Pending => '待处理',
            self::Processing => '处理中',
            self::Resolved => '已解决',
            self::Ignored => '不处理',
        };
    }
}
