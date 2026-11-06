<?php

namespace App\Filament\Resources\AppVersions\Schemas;

use App\Enums\Compatibility;
use App\Models\AppVersion;
use App\Models\Category;
use Filament\Forms\Components\FileUpload;
use Filament\Forms\Components\Select;
use Filament\Forms\Components\Textarea;
use Filament\Forms\Components\Toggle;
use Filament\Forms\Components\ToggleButtons;
use Filament\Infolists\Components\TextEntry;
use Filament\Schemas\Components\Section;
use Filament\Schemas\Components\Utilities\Get;
use Filament\Schemas\Schema;
use Illuminate\Support\Number;

class AppVersionForm
{
    public static function configure(Schema $schema): Schema
    {
        return $schema
            ->components([
                Section::make('安装包')
                    ->columnSpanFull()
                    ->columns(2)
                    ->schema([
                        ToggleButtons::make('upload_target')
                            ->label('上传到')
                            ->options([
                                'existing' => '已有应用',
                                'new' => '新应用',
                            ])
                            ->default('existing')
                            ->inline()
                            ->live()
                            ->visibleOn('create')
                            ->columnSpanFull(),
                        Select::make('app_id')
                            ->label('应用')
                            ->relationship('app', 'name')
                            ->searchable()
                            ->preload()
                            ->default(fn (): ?string => request()->query('app'))
                            ->required(fn (Get $get): bool => $get('upload_target') !== 'new')
                            ->visible(fn (Get $get, string $operation): bool => $operation === 'edit' || $get('upload_target') !== 'new')
                            ->disabledOn('edit')
                            ->columnSpanFull(),
                        Select::make('category_id')
                            ->label('分类')
                            ->options(fn () => Category::query()->orderBy('sort_order')->pluck('name', 'id'))
                            ->required(fn (Get $get): bool => $get('upload_target') === 'new')
                            ->visible(fn (Get $get, string $operation): bool => $operation === 'create' && $get('upload_target') === 'new'),
                        Toggle::make('is_listed')
                            ->label('上架')
                            ->default(true)
                            ->visible(fn (Get $get, string $operation): bool => $operation === 'create' && $get('upload_target') === 'new'),
                        Toggle::make('is_featured')
                            ->label('精选')
                            ->default(false)
                            ->visible(fn (Get $get, string $operation): bool => $operation === 'create' && $get('upload_target') === 'new'),
                        FileUpload::make('apk')
                            ->label('APK 文件')
                            ->helperText('只接受 .apk，最大 '.config('tvstore.max_apk_mb').' MB。上传后自动识别包名、版本、签名等信息，这些信息不能手动修改；选择「新应用」时会用识别出的包名和名称自动创建应用。')
                            ->required()
                            ->storeFiles(false)
                            ->maxSize(config('tvstore.max_apk_mb') * 1024)
                            ->rule('extensions:apk')
                            ->validationMessages(['extensions' => '只能上传 .apk 文件。'])
                            ->visibleOn('create')
                            ->columnSpanFull(),
                    ]),
                Section::make('识别结果')
                    ->description('从安装包读取，不能手动修改。')
                    ->columnSpanFull()
                    ->columns(2)
                    ->visibleOn('edit')
                    ->schema(self::identifiedEntries()),
                Section::make('兼容性与说明')
                    ->columnSpanFull()
                    ->schema([
                        Select::make('compatibility')
                            ->label('兼容状态')
                            ->options(Compatibility::class)
                            ->default(Compatibility::Unverified->value)
                            ->required(),
                        Textarea::make('test_device_notes')
                            ->label('测试设备说明')
                            ->rows(3)
                            ->maxLength(2000),
                        Textarea::make('known_issues')
                            ->label('已知问题')
                            ->rows(3)
                            ->maxLength(2000),
                        Textarea::make('release_notes')
                            ->label('更新说明')
                            ->rows(4)
                            ->maxLength(5000),
                    ]),
            ]);
    }

    /** Read-only values that came from the APK; shown on the edit page only. */
    private static function identifiedEntries(): array
    {
        return [
            TextEntry::make('status')->label('发布状态')->badge(),
            TextEntry::make('published_at')->label('首次发布时间')->dateTime()->placeholder('未发布'),
            TextEntry::make('package_name')->label('包名'),
            TextEntry::make('version_name')->label('versionName'),
            TextEntry::make('version_code')->label('versionCode'),
            TextEntry::make('min_sdk')->label('最低 SDK'),
            TextEntry::make('abis')
                ->label('CPU ABI')
                ->state(fn (AppVersion $record): string => $record->abis === [] ? '无原生库（全部架构可用）' : implode(', ', $record->abis)),
            TextEntry::make('size_bytes')
                ->label('文件大小')
                ->formatStateUsing(fn (int $state): string => Number::fileSize($state, 1).'（'.number_format($state).' 字节）'),
            TextEntry::make('sha256')->label('SHA-256')->copyable()->columnSpanFull(),
            TextEntry::make('signer_sha256')->label('签名证书 SHA-256')->copyable()->columnSpanFull(),
            TextEntry::make('download_url')
                ->label('下载地址')
                ->state(fn (AppVersion $record): string => $record->downloadUrl())
                ->copyable()
                ->columnSpanFull(),
        ];
    }
}
