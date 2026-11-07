<?php

namespace App\Enums;

use Filament\Support\Contracts\HasLabel;

enum AppEventType: string implements HasLabel
{
    case Download = 'download';
    case Install = 'install';

    public function getLabel(): string
    {
        return match ($this) {
            self::Download => '下载完成',
            self::Install => '安装成功',
        };
    }
}
