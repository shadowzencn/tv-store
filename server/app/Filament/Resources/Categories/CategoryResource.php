<?php

namespace App\Filament\Resources\Categories;

use App\Filament\Resources\Categories\Pages\ManageCategories;
use App\Models\Category;
use BackedEnum;
use Filament\Actions\DeleteAction;
use Filament\Actions\EditAction;
use Filament\Forms\Components\TextInput;
use Filament\Notifications\Notification;
use Filament\Resources\Resource;
use Filament\Schemas\Schema;
use Filament\Support\Icons\Heroicon;
use Filament\Tables\Columns\TextColumn;
use Filament\Tables\Table;

class CategoryResource extends Resource
{
    protected static ?string $model = Category::class;

    protected static string|BackedEnum|null $navigationIcon = Heroicon::OutlinedSquares2x2;

    protected static ?string $navigationLabel = '分类';

    protected static ?string $modelLabel = '分类';

    protected static ?string $pluralModelLabel = '分类';

    protected static ?int $navigationSort = 1;

    protected static ?string $recordTitleAttribute = 'name';

    public static function form(Schema $schema): Schema
    {
        return $schema
            ->components([
                TextInput::make('id')
                    ->label('标识（slug）')
                    ->helperText('小写字母、数字和连字符，电视端用它识别分类，创建后不能修改。')
                    ->required()
                    ->maxLength(64)
                    ->regex('/^[a-z0-9]+(?:-[a-z0-9]+)*$/')
                    ->unique(ignoreRecord: true)
                    ->disabledOn('edit'),
                TextInput::make('name')
                    ->label('名称')
                    ->required()
                    ->maxLength(50),
                TextInput::make('sort_order')
                    ->label('排序')
                    ->helperText('数字越小越靠前。')
                    ->required()
                    ->integer()
                    ->default(0),
            ]);
    }

    public static function table(Table $table): Table
    {
        return $table
            ->defaultSort('sort_order')
            ->reorderable('sort_order')
            ->columns([
                TextColumn::make('name')
                    ->label('名称')
                    ->searchable(),
                TextColumn::make('id')
                    ->label('标识')
                    ->searchable(),
                TextColumn::make('sort_order')
                    ->label('排序')
                    ->numeric()
                    ->sortable(),
                TextColumn::make('apps_count')
                    ->label('应用数')
                    ->counts('apps'),
                TextColumn::make('updated_at')
                    ->label('更新时间')
                    ->dateTime()
                    ->sortable()
                    ->toggleable(isToggledHiddenByDefault: true),
            ])
            ->recordActions([
                EditAction::make(),
                DeleteAction::make()
                    ->before(function (DeleteAction $action, Category $record): void {
                        if ($record->apps()->exists()) {
                            Notification::make()
                                ->danger()
                                ->title('不能删除该分类')
                                ->body('该分类下还有应用，请先把这些应用移到其他分类。')
                                ->send();
                            $action->cancel();
                        }
                    }),
            ]);
    }

    public static function getPages(): array
    {
        return [
            'index' => ManageCategories::route('/'),
        ];
    }
}
