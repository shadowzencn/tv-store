<?php

namespace App\Filament\Resources\AppEvents;

use App\Enums\AppEventType;
use App\Filament\Resources\AppEvents\Pages\ManageAppEvents;
use App\Models\AppEvent;
use BackedEnum;
use Filament\Resources\Resource;
use Filament\Support\Icons\Heroicon;
use Filament\Tables\Columns\TextColumn;
use Filament\Tables\Filters\SelectFilter;
use Filament\Tables\Table;

class AppEventResource extends Resource
{
    protected static ?string $model = AppEvent::class;

    protected static string|BackedEnum|null $navigationIcon = Heroicon::OutlinedChartBar;

    protected static ?string $navigationLabel = '下载与安装统计';

    protected static ?string $modelLabel = '统计记录';

    protected static ?string $pluralModelLabel = '下载与安装统计';

    protected static ?int $navigationSort = 5;

    public static function canCreate(): bool
    {
        return false;
    }

    public static function table(Table $table): Table
    {
        return $table
            ->modifyQueryUsing(fn ($query) => $query->with('app'))
            ->defaultSort('created_at', 'desc')
            ->columns([
                TextColumn::make('event_type')
                    ->label('动作')
                    ->badge()
                    ->color(fn (AppEventType $state): string => match ($state) {
                        AppEventType::Download => 'info',
                        AppEventType::Install => 'success',
                    }),
                TextColumn::make('app.name')
                    ->label('应用')
                    ->description(fn (AppEvent $record) => $record->package_name)
                    ->placeholder('未知应用')
                    ->searchable(['package_name']),
                TextColumn::make('version_name')
                    ->label('版本')
                    ->description(fn (AppEvent $record) => $record->version_code !== null ? 'versionCode '.$record->version_code : null)
                    ->placeholder('-'),
                TextColumn::make('device_model')
                    ->label('设备型号')
                    ->searchable(),
                TextColumn::make('ip_address')
                    ->label('用户 IP')
                    ->placeholder('-')
                    ->searchable(),
                TextColumn::make('android_version')
                    ->label('系统版本')
                    ->description(fn (AppEvent $record) => $record->abi),
                TextColumn::make('client_version')
                    ->label('商店版本'),
                TextColumn::make('created_at')
                    ->label('发生时间')
                    ->dateTime()
                    ->sortable(),
            ])
            ->filters([
                SelectFilter::make('event_type')
                    ->label('动作类型')
                    ->options(AppEventType::class),
                SelectFilter::make('app_id')
                    ->label('应用')
                    ->relationship('app', 'name')
                    ->searchable(),
            ]);
    }

    public static function getPages(): array
    {
        return [
            'index' => ManageAppEvents::route('/'),
        ];
    }
}
