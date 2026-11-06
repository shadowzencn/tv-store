<?php

namespace App\Filament\Resources\AppVersions;

use App\Enums\VersionStatus;
use App\Models\AppVersion;
use App\Services\AppVersionPublisher;
use App\Services\VersionRuleException;
use Filament\Actions\Action;
use Filament\Notifications\Notification;
use Filament\Resources\Pages\EditRecord;
use Filament\Support\Icons\Heroicon;
use Livewire\Component;

/**
 * Publish / withdraw / delete actions shared by the version table and the version edit page.
 * Every state change goes through AppVersionPublisher, which also writes the audit log.
 */
class VersionActions
{
    public static function publish(): Action
    {
        return Action::make('publish')
            ->label('发布')
            ->icon(Heroicon::OutlinedRocketLaunch)
            ->color('success')
            ->requiresConfirmation()
            ->modalHeading('发布该版本')
            ->modalDescription('发布后电视端会看到这个版本；该应用当前已发布的版本会变为历史版本。')
            ->modalSubmitActionLabel('确认发布')
            ->visible(fn (AppVersion $record): bool => $record->status !== VersionStatus::Published)
            ->action(function (AppVersion $record, AppVersionPublisher $publisher): void {
                self::run(fn () => $publisher->publish($record), '版本已发布');
            });
    }

    public static function withdraw(): Action
    {
        return Action::make('withdraw')
            ->label('撤回')
            ->icon(Heroicon::OutlinedArrowUturnLeft)
            ->color('warning')
            ->requiresConfirmation()
            ->modalHeading('撤回该版本')
            ->modalDescription('撤回后电视端不再提供该应用下载，直到重新发布一个版本。安装包文件会保留。')
            ->modalSubmitActionLabel('确认撤回')
            ->visible(fn (AppVersion $record): bool => $record->status === VersionStatus::Published)
            ->action(function (AppVersion $record, AppVersionPublisher $publisher): void {
                self::run(fn () => $publisher->withdraw($record), '版本已撤回');
            });
    }

    public static function deleteDraft(): Action
    {
        return Action::make('deleteDraft')
            ->label('删除')
            ->icon(Heroicon::OutlinedTrash)
            ->color('danger')
            ->requiresConfirmation()
            ->modalHeading('删除草稿版本')
            ->modalDescription('将删除该版本记录和服务器上的安装包文件，无法恢复。')
            ->modalSubmitActionLabel('确认删除')
            ->visible(fn (AppVersion $record): bool => $record->status === VersionStatus::Draft)
            ->action(function (AppVersion $record, AppVersionPublisher $publisher, Component $livewire): void {
                $deleted = self::run(fn () => $publisher->deleteDraft($record), '草稿版本已删除');

                if ($deleted && $livewire instanceof EditRecord) {
                    $livewire->redirect(AppVersionResource::getUrl('index'));
                }
            });
    }

    private static function run(callable $operation, string $successTitle): bool
    {
        try {
            $operation();
        } catch (VersionRuleException $e) {
            Notification::make()->danger()->title('操作未执行')->body($e->getMessage())->send();

            return false;
        }

        Notification::make()->success()->title($successTitle)->send();

        return true;
    }
}
