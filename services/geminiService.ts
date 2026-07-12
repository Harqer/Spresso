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
    turnstileToken?: string,
    onProgress?: (text: string) => void
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

    const reader = response.body?.getReader();
    if (!reader) throw new Error("ReadableStream not supported");
    
    const decoder = new TextDecoder("utf-8");
    let resultData: any = null;
    let buffer = "";
    let accumulatedText = "";

    while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        
        const lines = buffer.split('\n\n');
        buffer = lines.pop() || ''; 
        
        for (const line of lines) {
            if (line.startsWith('data: ')) {
                const payload = line.slice(6);
                try {
                    const event = JSON.parse(payload);
                    if (event.type === 'thought') {
                        accumulatedText += event.text;
                        if (onProgress) onProgress(accumulatedText);
                    } else if (event.type === 'result') {
                        resultData = event.data;
                    }
                } catch {
                    // Ignore parse errors on incomplete chunks
                }
            }
        }
    }

    if (!resultData) {
        // Fallback if no result data was sent
        return { text: accumulatedText || "No response received." };
    }

    const result: any = {
        text: accumulatedText || resultData.response,
        vto_image_url: resultData.vto_image_url,
        vto_video_url: resultData.vto_video_url,
        citation: resultData.citation,
        grid: resultData.grid,
        compare: resultData.compare,
        filters: resultData.filters
    };

    if (resultData.intent === 'ADD_TO_CART' && resultData.product_id) {
        result.action = { type: 'ADD_TO_CART', id: resultData.product_id };
    }

    return result;

  } catch (error) {
    console.error("Vaultier Discovery Error:", error);
    return { text: "I apologize, but I seem to be having trouble reaching our archives at the moment." };
  }
};
