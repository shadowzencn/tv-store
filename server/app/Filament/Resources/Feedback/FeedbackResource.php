<?php

namespace App\Filament\Resources\Feedback;

use App\Enums\FeedbackStatus;
use App\Enums\FeedbackType;
use App\Filament\Resources\Feedback\Pages\ManageFeedback;
use App\Models\Feedback;
use BackedEnum;
use Filament\Actions\EditAction;
use Filament\Forms\Components\Select;
use Filament\Forms\Components\Textarea;
use Filament\Infolists\Components\TextEntry;
use Filament\Resources\Resource;
use Filament\Schemas\Components\Section;
use Filament\Schemas\Schema;
use Filament\Support\Icons\Heroicon;
use Filament\Tables\Columns\TextColumn;
use Filament\Tables\Filters\SelectFilter;
use Filament\Tables\Table;

/**
 * Feedback comes only from the TV client; administrators can read it and update the handling state.
 */
class FeedbackResource extends Resource
{
    protected static ?string $model = Feedback::class;

    protected static string|BackedEnum|null $navigationIcon = Heroicon::OutlinedChatBubbleLeftRight;

    protected static ?string $navigationLabel = '反馈';

    protected static ?string $modelLabel = '反馈';

    protected static ?string $pluralModelLabel = '反馈';

    protected static ?int $navigationSort = 4;

    public static function canCreate(): bool
    {
        return false;
    }

    public static function getNavigationBadge(): ?string
    {
        $pending = Feedback::query()->where('status', FeedbackStatus::Pending->value)->count();

        return $pending > 0 ? (string) $pending : null;
    }

    public static function form(Schema $schema): Schema
    {
        return $schema
            ->components([
                Section::make('反馈内容')
                    ->columnSpanFull()
                    ->columns(2)
                    ->schema([
                        TextEntry::make('type')->label('类型')->badge(),
                        TextEntry::make('app.name')->label('应用')->placeholder('未指定'),
                        TextEntry::make('device_model')->label('设备型号'),
                        TextEntry::make('android_version')->label('Android 版本'),
                        TextEntry::make('client_version')->label('客户端版本'),
                        TextEntry::make('created_at')->label('提交时间')->dateTime(),
                        TextEntry::make('description')->label('描述')->placeholder('（无）')->columnSpanFull(),
                    ]),
                Select::make('status')
                    ->label('处理状态')
                    ->options(FeedbackStatus::class)
                    ->required(),
                Textarea::make('handling_note')
                    ->label('处理说明')
                    ->rows(3)
                    ->maxLength(2000)
                    ->columnSpanFull(),
            ]);
    }

    public static function table(Table $table): Table
    {
        return $table
            ->modifyQueryUsing(fn ($query) => $query->with('app'))
            ->defaultSort('created_at', 'desc')
            ->columns([
                TextColumn::make('type')
                    ->label('类型')
                    ->badge(),
                TextColumn::make('app.name')
                    ->label('应用')
                    ->placeholder('未指定'),
                TextColumn::make('description')
                    ->label('描述')
                    ->limit(40)
                    ->placeholder('（无）')
                    ->searchable(),
                TextColumn::make('device_model')
                    ->label('设备')
                    ->searchable(),
                TextColumn::make('android_version')
                    ->label('系统版本'),
                TextColumn::make('client_version')
                    ->label('客户端版本'),
                TextColumn::make('status')
                    ->label('处理状态')
                    ->badge()
                    ->color(fn (FeedbackStatus $state): string => match ($state) {
                        FeedbackStatus::Pending => 'warning',
                        FeedbackStatus::Processing => 'info',
                        FeedbackStatus::Resolved => 'success',
                        FeedbackStatus::Ignored => 'gray',
                    }),
                TextColumn::make('created_at')
                    ->label('提交时间')
                    ->dateTime()
                    ->sortable(),
            ])
            ->filters([
                SelectFilter::make('status')
                    ->label('处理状态')
                    ->options(FeedbackStatus::class),
                SelectFilter::make('type')
                    ->label('类型')
                    ->options(FeedbackType::class),
                SelectFilter::make('app_id')
                    ->label('应用')
                    ->relationship('app', 'name')
                    ->searchable(),
            ])
            ->recordActions([
                EditAction::make()
                    ->label('处理')
                    ->modalHeading('处理反馈'),
            ]);
    }

    public static function getPages(): array
    {
        return [
            'index' => ManageFeedback::route('/'),
        ];
    }
}
