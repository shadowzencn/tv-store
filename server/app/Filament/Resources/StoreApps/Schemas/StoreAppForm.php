<?php

namespace App\Filament\Resources\StoreApps\Schemas;

use App\Models\StoreApp;
use App\Providers\AppServiceProvider;
use Filament\Forms\Components\FileUpload;
use Filament\Forms\Components\Select;
use Filament\Forms\Components\Textarea;
use Filament\Forms\Components\TextInput;
use Filament\Forms\Components\Toggle;
use Filament\Schemas\Components\Section;
use Filament\Schemas\Schema;

class StoreAppForm
{
    public const ICON_MAX_KB = 1024;

    public static function configure(Schema $schema): Schema
    {
        return $schema
            ->components([
                Section::make('基本信息')
                    ->columns(2)
                    ->columnSpanFull()
                    ->schema([
                        TextInput::make('id')
                            ->label('标识（slug）')
                            ->helperText('小写字母、数字和连字符，电视端用它识别应用，创建后不能修改。')
                            ->required()
                            ->maxLength(64)
                            ->regex('/^[a-z0-9]+(?:-[a-z0-9]+)*$/')
                            ->unique(ignoreRecord: true)
                            ->disabledOn('edit'),
                        TextInput::make('package_name')
                            ->label('包名')
                            ->helperText('上传的安装包包名必须与此一致；已有版本后不能修改。')
                            ->required()
                            ->maxLength(255)
                            ->regex('/^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$/')
                            ->unique(ignoreRecord: true)
                            ->disabled(fn (?StoreApp $record): bool => $record?->versions()->exists() ?? false),
                        TextInput::make('name')
                            ->label('名称')
                            ->required()
                            ->maxLength(100),
                        Select::make('category_id')
                            ->label('分类')
                            ->relationship('category', 'name', fn ($query) => $query->orderBy('sort_order'))
                            ->preload()
                            ->required(),
                        TextInput::make('summary')
                            ->label('简介')
                            ->maxLength(200)
                            ->columnSpanFull(),
                        Textarea::make('description')
                            ->label('说明')
                            ->rows(6)
                            ->columnSpanFull(),
                    ]),
                Section::make('展示')
                    ->columns(2)
                    ->columnSpanFull()
                    ->schema([
                        Toggle::make('is_listed')
                            ->label('上架')
                            ->helperText('上架且有已发布版本的应用才会出现在电视端。')
                            ->default(false),
                        Toggle::make('is_featured')
                            ->label('精选')
                            ->default(false),
                        TextInput::make('sort_order')
                            ->label('排序')
                            ->helperText('数字越小越靠前。')
                            ->required()
                            ->integer()
                            ->default(0),
                        FileUpload::make('icon_path')
                            ->label('图标')
                            ->helperText('PNG、JPEG 或 WebP，不超过 1 MB，建议 512×512 正方形。')
                            ->image()
                            ->acceptedFileTypes(['image/png', 'image/jpeg', 'image/webp'])
                            ->maxSize(self::ICON_MAX_KB)
                            ->disk(AppServiceProvider::FILES_DISK)
                            ->directory('icons')
                            ->visibility('public'),
                    ]),
            ]);
    }
}
