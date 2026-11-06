<?php

namespace App\Filament\Resources\AppVersions\Tables;

use App\Enums\Compatibility;
use App\Enums\VersionStatus;
use App\Filament\Resources\AppVersions\VersionActions;
use App\Models\AppVersion;
use Filament\Actions\ActionGroup;
use Filament\Actions\EditAction;
use Filament\Tables\Columns\TextColumn;
use Filament\Tables\Filters\SelectFilter;
use Filament\Tables\Table;
use Illuminate\Support\Number;

class AppVersionsTable
{
    public static function configure(Table $table): Table
    {
        return $table
            ->modifyQueryUsing(fn ($query) => $query->with('app'))
            ->defaultSort('created_at', 'desc')
            ->columns([
                TextColumn::make('app.name')
                    ->label('应用')
                    ->description(fn (AppVersion $record): string => $record->package_name)
                    ->searchable(['package_name']),
                TextColumn::make('version_name')
                    ->label('版本')
                    ->description(fn (AppVersion $record): string => 'versionCode '.$record->version_code)
                    ->searchable(),
                TextColumn::make('status')
                    ->label('状态')
                    ->badge()
                    ->color(fn (VersionStatus $state): string => match ($state) {
                        VersionStatus::Published => 'success',
                        VersionStatus::Draft => 'gray',
                        VersionStatus::Withdrawn => 'danger',
                        VersionStatus::Superseded => 'info',
                    }),
                TextColumn::make('compatibility')
                    ->label('兼容状态')
                    ->badge(),
                TextColumn::make('abis')
                    ->label('CPU ABI')
                    ->state(fn (AppVersion $record): string => $record->abis === [] ? '无原生库' : implode(', ', $record->abis)),
                TextColumn::make('min_sdk')
                    ->label('最低 SDK'),
                TextColumn::make('size_bytes')
                    ->label('大小')
                    ->formatStateUsing(fn (int $state): string => Number::fileSize($state, 1)),
                TextColumn::make('published_at')
                    ->label('首次发布')
                    ->dateTime()
                    ->placeholder('未发布')
                    ->sortable(),
                TextColumn::make('created_at')
                    ->label('上传时间')
                    ->dateTime()
                    ->sortable(),
            ])
            ->filters([
                SelectFilter::make('app_id')
                    ->label('应用')
                    ->relationship('app', 'name')
                    ->searchable()
                    ->preload(),
                SelectFilter::make('status')
                    ->label('状态')
                    ->options(VersionStatus::class),
                SelectFilter::make('compatibility')
                    ->label('兼容状态')
                    ->options(Compatibility::class),
            ])
            ->recordActions([
                EditAction::make(),
                ActionGroup::make([
                    VersionActions::publish(),
                    VersionActions::withdraw(),
                    VersionActions::deleteDraft(),
                ]),
            ]);
    }
}
