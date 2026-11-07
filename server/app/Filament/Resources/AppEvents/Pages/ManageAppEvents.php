<?php

namespace App\Filament\Resources\AppEvents\Pages;

use App\Filament\Resources\AppEvents\AppEventResource;
use Filament\Resources\Pages\ManageRecords;

class ManageAppEvents extends ManageRecords
{
    protected static string $resource = AppEventResource::class;
}
