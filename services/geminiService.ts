/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
*/

export const sendMessageToGemini = async (
    history: {role: string, text: string}[],
    newMessage: string,
    userToken: string,
    cartItems: any[] = [],
    imageBase64?: string,
    turnstileToken?: string
): Promise<{
    text: string,
    action?: {type: string, id: string},
    grid?: string[],
    compare?: string[],
    vto_image_url?: string,
    vto_video_url?: string,
    citation?: {source: string, url: string}
}> => {
  try {
    const response = await fetch('/discovery/chat', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${userToken}`,
            'X-Turnstile-Token': turnstileToken || '',
            'X-Client-Platform': 'web'
        },
        body: JSON.stringify({
            message: newMessage,
            cart_items: cartItems,
            image_base64: imageBase64
        })
    });

    if (!response.ok) {
        throw new Error("Vaultier intelligence is currently offline.");
    }

    const data = await response.json();

    const result: any = {
        text: data.response,
        vto_image_url: data.vto_image_url,
        vto_video_url: data.vto_video_url,
        citation: data.citation,
        grid: data.grid,
        compare: data.compare,
        filters: data.filters
    };

    if (data.intent === 'ADD_TO_CART' && data.product_id) {
        result.action = { type: 'ADD_TO_CART', id: data.product_id };
    }

    return result;

  } catch (error) {
    console.error("Vaultier Discovery Error:", error);
    return { text: "I apologize, but I seem to be having trouble reaching our archives at the moment." };
  }
};
