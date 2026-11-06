<?php

namespace App\Filament\Resources\StoreApps\Pages;

use App\Filament\Resources\AppVersions\AppVersionResource;
use App\Filament\Resources\StoreApps\StoreAppResource;
use App\Models\StoreApp;
use App\Providers\AppServiceProvider;
use Filament\Actions\Action;
use Filament\Actions\DeleteAction;
use Filament\Resources\Pages\EditRecord;
use Filament\Support\Icons\Heroicon;
use Illuminate\Support\Facades\Storage;

class EditStoreApp extends EditRecord
{
    protected static string $resource = StoreAppResource::class;

    private ?string $previousIconPath = null;

    protected function getHeaderActions(): array
    {
        return [
            Action::make('uploadVersion')
                ->label('上传新版本')
                ->icon(Heroicon::OutlinedArrowUpTray)
                ->url(fn (StoreApp $record): string => AppVersionResource::getUrl('create', ['app' => $record->id])),
            Action::make('versions')
                ->label('查看版本')
                ->color('gray')
                ->url(fn (StoreApp $record): string => AppVersionResource::getUrl('index', [
                    'filters' => ['app_id' => ['value' => $record->id]],
                ])),
            // Apps that have versions are kept: versions are history and the foreign key forbids it.
            DeleteAction::make()
                ->visible(fn (StoreApp $record): bool => ! $record->versions()->exists())
                ->after(function (StoreApp $record): void {
                    if ($record->icon_path !== null) {
                        Storage::disk(AppServiceProvider::FILES_DISK)->delete($record->icon_path);
                    }
                }),
        ];
    }

    protected function beforeSave(): void
    {
        $this->previousIconPath = $this->getRecord()->getOriginal('icon_path');
    }

    protected function afterSave(): void
    {
        $current = $this->getRecord()->icon_path;
        if ($this->previousIconPath !== null && $this->previousIconPath !== $current) {
            Storage::disk(AppServiceProvider::FILES_DISK)->delete($this->previousIconPath);
        }
    }
}
