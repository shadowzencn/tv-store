<?php

namespace App\Filament\Resources\StoreApps\Pages;

use App\Filament\Resources\StoreApps\StoreAppResource;
use App\Models\Category;
use App\Services\AppVersionUploader;
use App\Services\VersionRuleException;
use App\Support\Apk\ApkInspectionException;
use Filament\Notifications\Notification;
use Filament\Resources\Pages\CreateRecord;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Support\Arr;
use Illuminate\Validation\ValidationException;
use Livewire\Features\SupportFileUploads\FileUploadConfiguration;
use Livewire\Features\SupportFileUploads\TemporaryUploadedFile;

class CreateStoreApp extends CreateRecord
{
    protected static string $resource = StoreAppResource::class;

    protected static bool $canCreateAnother = false;

    protected function handleRecordCreation(array $data): Model
    {
        $file = Arr::first(Arr::wrap($data['apk'] ?? null));
        if (! $file instanceof TemporaryUploadedFile) {
            throw ValidationException::withMessages(['data.apk' => '请上传 APK 文件。']);
        }

        $category = Category::query()->orderBy('sort_order')->orderBy('id')->first();
        if ($category === null) {
            $this->data['apk'] = [];
            $this->deleteTemporaryUpload($file);

            throw ValidationException::withMessages(['data.apk' => '请先创建一个分类，再上传应用。']);
        }

        try {
            $version = app(AppVersionUploader::class)->uploadToNewApp($file->getRealPath(), [
                'category_id' => $category->id,
                'is_listed' => true,
                'is_featured' => false,
            ]);

            return $version->app;
        } catch (ApkInspectionException|VersionRuleException $e) {
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

    protected function getCreatedNotification(): ?Notification
    {
        return Notification::make()
            ->success()
            ->title('应用已从安装包创建')
            ->body('包名、名称和启动图标已从安装包读取，版本保存为草稿。没有图标时可在此页补传。');
    }

    protected function getRedirectUrl(): string
    {
        return StoreAppResource::getUrl('edit', ['record' => $this->getRecord()]);
    }
}
