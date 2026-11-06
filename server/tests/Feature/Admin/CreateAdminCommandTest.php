<?php

namespace Tests\Feature\Admin;

use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

class CreateAdminCommandTest extends TestCase
{
    use RefreshDatabase;

    protected function tearDown(): void
    {
        putenv('TVSTORE_ADMIN_PASSWORD');
        parent::tearDown();
    }

    public function test_creates_admin_with_password_from_environment(): void
    {
        putenv('TVSTORE_ADMIN_PASSWORD=correct-horse-battery');

        $this->artisan('tvstore:create-admin', ['email' => 'Admin@Example.com', '--name' => '管理员', '--no-interaction' => true])
            ->assertSuccessful();

        $user = User::query()->where('email', 'admin@example.com')->sole();
        $this->assertSame('管理员', $user->name);
        $this->assertTrue(Hash::check('correct-horse-battery', $user->password));
    }

    public function test_prompts_for_password_when_environment_is_empty(): void
    {
        $this->artisan('tvstore:create-admin', ['email' => 'ops@example.com'])
            ->expectsQuestion('密码（至少 12 个字符）', 'a-long-secret-123')
            ->expectsQuestion('再次输入密码', 'a-long-secret-123')
            ->assertSuccessful();

        $user = User::query()->where('email', 'ops@example.com')->sole();
        $this->assertSame('ops', $user->name);
        $this->assertTrue(Hash::check('a-long-secret-123', $user->password));
    }

    public function test_rejects_short_password_duplicate_email_and_missing_password(): void
    {
        putenv('TVSTORE_ADMIN_PASSWORD=short');
        $this->artisan('tvstore:create-admin', ['email' => 'a@example.com', '--no-interaction' => true])->assertFailed();

        putenv('TVSTORE_ADMIN_PASSWORD');
        $this->artisan('tvstore:create-admin', ['email' => 'a@example.com', '--no-interaction' => true])->assertFailed();

        User::factory()->create(['email' => 'b@example.com']);
        putenv('TVSTORE_ADMIN_PASSWORD=correct-horse-battery');
        $this->artisan('tvstore:create-admin', ['email' => 'b@example.com', '--no-interaction' => true])->assertFailed();

        $this->assertDatabaseCount('users', 1);
    }
}
