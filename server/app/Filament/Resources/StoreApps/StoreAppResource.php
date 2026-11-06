<?php

namespace App\Filament\Resources\StoreApps;

use App\Filament\Resources\StoreApps\Pages\CreateStoreApp;
use App\Filament\Resources\StoreApps\Pages\EditStoreApp;
use App\Filament\Resources\StoreApps\Pages\ListStoreApps;
use App\Filament\Resources\StoreApps\Schemas\StoreAppForm;
use App\Filament\Resources\StoreApps\Tables\StoreAppsTable;
use App\Models\StoreApp;
use BackedEnum;
use Filament\Resources\Resource;
use Filament\Schemas\Schema;
use Filament\Support\Icons\Heroicon;
use Filament\Tables\Table;

class StoreAppResource extends Resource
{
    protected static ?string $model = StoreApp::class;

    protected static string|BackedEnum|null $navigationIcon = Heroicon::OutlinedTv;

    protected static ?string $navigationLabel = '应用';

    protected static ?string $modelLabel = '应用';

    protected static ?string $pluralModelLabel = '应用';

    protected static ?int $navigationSort = 2;

    protected static ?string $slug = 'apps';

    protected static ?string $recordTitleAttribute = 'name';

    public static function form(Schema $schema): Schema
    {
        return StoreAppForm::configure($schema);
    }

    public static function table(Table $table): Table
    {
        return StoreAppsTable::configure($table);
    }

    public static function getPages(): array
    {
        return [
            'index' => ListStoreApps::route('/'),
            'create' => CreateStoreApp::route('/create'),
            'edit' => EditStoreApp::route('/{record}/edit'),
        ];
    }
}
