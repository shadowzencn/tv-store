<?php

namespace App\Filament\Resources\AppVersions\Pages;

use App\Filament\Resources\AppVersions\AppVersionResource;
use App\Models\StoreApp;
use App\Services\AppVersionUploader;
use App\Services\VersionRuleException;
use App\Support\Apk\ApkInspectionException;
use Filament\Resources\Pages\CreateRecord;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Support\Arr;
use Illuminate\Validation\ValidationException;
use Livewire\Features\SupportFileUploads\FileUploadConfiguration;
use Livewire\Features\SupportFileUploads\TemporaryUploadedFile;

class CreateAppVersion extends CreateRecord
{
    protected static string $resource = AppVersionResource::class;

    protected static ?string $title = '上传版本';

    protected static bool $canCreateAnother = false;

    protected function handleRecordCreation(array $data): Model
    {
        $file = Arr::first(Arr::wrap($data['apk'] ?? null));
        if (! $file instanceof TemporaryUploadedFile) {
            throw ValidationException::withMessages(['data.apk' => '请上传 APK 文件。']);
        }

        try {
            return app(AppVersionUploader::class)->upload(
                StoreApp::query()->findOrFail($data['app_id']),
                $file->getRealPath(),
                $data,
            );
        } catch (ApkInspectionException|VersionRuleException $e) {
            // The temporary file is deleted below, so the field must be emptied for a fresh upload.
            $this->data['apk'] = [];

            throw ValidationException::withMessages(['data.apk' => $e->getMessage()]);
        } finally {
            $this->deleteTemporaryUpload($file);
        }
    }

    // TemporaryUploadedFile::delete() leaves Livewire's "<name>.json" metadata file behind.
    private function deleteTemporaryUpload(TemporaryUploadedFile $file): void
    {
        $file->delete();
        FileUploadConfiguration::storage()->delete(FileUploadConfiguration::path($file->getFilename().'.json'));
    }

    protected function getRedirectUrl(): string
    {
        return AppVersionResource::getUrl('edit', ['record' => $this->getRecord()]);
    }
}
