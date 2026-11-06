<?php

namespace Tests\Feature;

use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class SmokeTest extends TestCase
{
    use RefreshDatabase;

    public function test_admin_login_page_loads(): void
    {
        $this->get('/admin/login')->assertOk();
    }

    public function test_admin_dashboard_requires_login(): void
    {
        $this->get('/admin')->assertRedirect('/admin/login');
    }

    public function test_unknown_api_route_uses_error_format(): void
    {
        $this->getJson('/api/v1/nope')
            ->assertNotFound()
            ->assertExactJson(['error' => ['code' => 'not_found', 'message' => '请求的资源不存在。']]);
    }

    public function test_wrong_method_uses_error_format(): void
    {
        $this->getJson('/api/v1/feedback')
            ->assertStatus(405)
            ->assertJsonPath('error.code', 'method_not_allowed');
    }
}
