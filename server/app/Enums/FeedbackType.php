<?php

namespace App\Enums;

use Filament\Support\Contracts\HasLabel;

enum FeedbackType: string implements HasLabel
{
    case InstallFailed = 'install_failed';
    case LaunchFailed = 'launch_failed';
    case RuntimeError = 'runtime_error';
    case DownloadFailed = 'download_failed';
    case Other = 'other';

    public function getLabel(): string
    {
        return match ($this) {
            self::InstallFailed => '无法安装',
            self::LaunchFailed => '无法启动',
            self::RuntimeError => '运行异常',
            self::DownloadFailed => '下载失败',
            self::Other => '其他问题',
        };
    }
}
