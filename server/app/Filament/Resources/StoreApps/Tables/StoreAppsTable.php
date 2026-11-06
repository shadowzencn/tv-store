<?php

namespace App\Filament\Resources\StoreApps\Tables;

use App\Providers\AppServiceProvider;
use Filament\Actions\EditAction;
use Filament\Tables\Columns\IconColumn;
use Filament\Tables\Columns\ImageColumn;
use Filament\Tables\Columns\TextColumn;
use Filament\Tables\Filters\SelectFilter;
use Filament\Tables\Filters\TernaryFilter;
use Filament\Tables\Table;

class StoreAppsTable
{
    public static function configure(Table $table): Table
    {
        return $table
            ->modifyQueryUsing(fn ($query) => $query->with(['category', 'publishedVersion']))
            ->defaultSort('sort_order')
            ->columns([
                ImageColumn::make('icon_path')
                    ->label('图标')
                    ->disk(AppServiceProvider::FILES_DISK)
                    ->visibility('public'),
                TextColumn::make('name')
                    ->label('名称')
                    ->description(fn ($record) => $record->package_name)
                    ->searchable(['name', 'package_name', 'id']),
                TextColumn::make('id')
                    ->label('标识')
                    ->toggleable(),
                TextColumn::make('category.name')
                    ->label('分类'),
                TextColumn::make('publishedVersion.version_name')
                    ->label('当前发布版本')
                    ->description(fn ($record) => $record->publishedVersion ? 'versionCode '.$record->publishedVersion->version_code : null)
                    ->placeholder('无'),
                IconColumn::make('is_listed')
                    ->label('上架')
                    ->boolean(),
                IconColumn::make('is_featured')
                    ->label('精选')
                    ->boolean(),
                TextColumn::make('sort_order')
                    ->label('排序')
                    ->numeric()
                    ->sortable(),
                TextColumn::make('updated_at')
                    ->label('更新时间')
                    ->dateTime()
                    ->sortable()
                    ->toggleable(isToggledHiddenByDefault: true),
            ])
            ->filters([
                SelectFilter::make('category')
                    ->label('分类')
                    ->relationship('category', 'name'),
                TernaryFilter::make('is_listed')
                    ->label('上架'),
                TernaryFilter::make('is_featured')
                    ->label('精选'),
            ])
            ->recordActions([
                EditAction::make(),
            ]);
    }
}
