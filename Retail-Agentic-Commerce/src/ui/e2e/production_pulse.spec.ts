import { test, expect } from '@playwright/test';

test('Vaultier Agentic Pulse - Chat & Reasoning', async ({ page }) => {
  // 1. Launch Vaultier industrial UI
  await page.goto('http://localhost:3000');

  // 2. Assert Identity Branding
  await expect(page.locator('text=Vaultier')).toBeVisible();

  // 3. Simulate Agentic Interaction
  const promptInput = page.getByPlaceholder(/ask Vaultier/i);
  await promptInput.fill('Find me biomorphic chrome glasses');
  await page.keyboard.press('Enter');

  // 4. Assert Reasoning Loop
  // The system should show "Vaultier is reasoning..." or similar
  await expect(page.locator('text=reasoning')).toBeVisible();

  // 5. Assert Industrial Response
  // Wait for the model to respond with grounded discovery
  await expect(page.locator('text=verified')).toBeVisible({ timeout: 30000 });
});

test('Vaultier Buy Button - Biometric Trigger', async ({ page }) => {
  await page.goto('http://localhost:3000');

  // Assuming a product card is visible
  const buyButton = page.locator('button:has-text("Buy")').first();
  if (await buyButton.isVisible()) {
    await buyButton.click();
    // Verify checkout protocol shift
    await expect(page.locator('text=Checkout')).toBeVisible();
  }
});
