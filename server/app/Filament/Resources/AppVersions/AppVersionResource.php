<?php

namespace App\Filament\Resources\AppVersions;

use App\Filament\Resources\AppVersions\Pages\CreateAppVersion;
use App\Filament\Resources\AppVersions\Pages\EditAppVersion;
use App\Filament\Resources\AppVersions\Pages\ListAppVersions;
use App\Filament\Resources\AppVersions\Schemas\AppVersionForm;
use App\Filament\Resources\AppVersions\Tables\AppVersionsTable;
use App\Models\AppVersion;
use BackedEnum;
use Filament\Resources\Resource;
use Filament\Schemas\Schema;
use Filament\Support\Icons\Heroicon;
use Filament\Tables\Table;

class AppVersionResource extends Resource
{
    protected static ?string $model = AppVersion::class;

    protected static string|BackedEnum|null $navigationIcon = Heroicon::OutlinedArchiveBox;

    protected static ?string $navigationLabel = '版本';

    protected static ?string $modelLabel = '版本';

    protected static ?string $pluralModelLabel = '版本';

    protected static ?int $navigationSort = 3;

    protected static ?string $slug = 'versions';

    protected static ?string $recordTitleAttribute = 'version_name';

    public static function form(Schema $schema): Schema
    {
        return AppVersionForm::configure($schema);
    }

    public static function table(Table $table): Table
    {
        return AppVersionsTable::configure($table);
    }

    public static function getPages(): array
    {
        return [
            'index' => ListAppVersions::route('/'),
            'create' => CreateAppVersion::route('/create'),
            'edit' => EditAppVersion::route('/{record}/edit'),
        ];
    }
}
