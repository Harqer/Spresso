import { GoogleGenAI } from '@google/genai';
import dotenv from 'dotenv';
dotenv.config();
const ai = new GoogleGenAI({});
async function test() {
  try {
    const responseStream = await ai.models.generateContentStream({
      model: 'gemini-2.0-flash-thinking-exp-01-21',
      contents: 'Think step by step and output {"result": "success"} in JSON format',
      // No config needed for thinking models, they always think? Let's check what it returns
    });
    for await (const chunk of responseStream) {
      console.log(JSON.stringify(chunk.candidates[0].content.parts, null, 2));
      break; 
    }
  } catch(e) { console.error(e) }
}
test();
