<?php

namespace App\Filament\Resources\AppVersions\Pages;

use App\Filament\Resources\AppVersions\AppVersionResource;
use App\Filament\Resources\AppVersions\VersionActions;
use App\Services\AppVersionUploader;
use Filament\Resources\Pages\EditRecord;

class EditAppVersion extends EditRecord
{
    protected static string $resource = AppVersionResource::class;

    protected function getHeaderActions(): array
    {
        return [
            VersionActions::publish(),
            VersionActions::withdraw(),
            VersionActions::deleteDraft(),
        ];
    }

    protected function mutateFormDataBeforeSave(array $data): array
    {
        // Values identified from the APK must never be changed by hand.
        return array_intersect_key($data, array_flip(AppVersionUploader::EDITABLE_FIELDS));
    }
}
