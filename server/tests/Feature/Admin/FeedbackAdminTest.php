<?php

namespace Tests\Feature\Admin;

use App\Enums\FeedbackStatus;
use App\Filament\Resources\Feedback\Pages\ManageFeedback;
use App\Models\Feedback;
use Filament\Actions\CreateAction;
use Filament\Actions\Testing\TestAction;
use Livewire\Livewire;

class FeedbackAdminTest extends AdminTestCase
{
    private function feedback(array $overrides = []): Feedback
    {
        return Feedback::query()->create(array_merge([
            'type' => 'install_failed',
            'description' => '安装后打不开',
            'device_model' => 'TCL Q10L',
            'android_version' => '11',
            'client_version' => '0.1.0',
        ], $overrides));
    }

    public function test_list_can_be_filtered_by_status(): void
    {
        $pending = $this->feedback();
        $resolved = $this->feedback(['status' => 'resolved']);

        Livewire::test(ManageFeedback::class)
            ->assertCanSeeTableRecords([$pending, $resolved])
            ->filterTable('status', 'pending')
            ->assertCanSeeTableRecords([$pending])
            ->assertCanNotSeeTableRecords([$resolved]);
    }

    public function test_status_and_note_can_be_updated_but_content_is_read_only(): void
    {
        $feedback = $this->feedback();

        Livewire::test(ManageFeedback::class)
            ->callAction(TestAction::make('edit')->table($feedback), [
                'status' => FeedbackStatus::Resolved->value,
                'handling_note' => '已在 1.0.1 修复',
                'device_model' => 'changed',
            ])
            ->assertHasNoFormErrors();

        $feedback->refresh();
        $this->assertSame(FeedbackStatus::Resolved, $feedback->status);
        $this->assertSame('已在 1.0.1 修复', $feedback->handling_note);
        $this->assertSame('TCL Q10L', $feedback->device_model);
    }

    public function test_feedback_cannot_be_created_or_deleted_in_admin(): void
    {
        $feedback = $this->feedback();

        Livewire::test(ManageFeedback::class)
            ->assertActionDoesNotExist(CreateAction::class)
            ->assertActionDoesNotExist(TestAction::make('delete')->table($feedback));
    }
}
