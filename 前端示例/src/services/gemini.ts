import { GoogleGenAI } from "@google/genai";
import { ChatMessage, Policy } from "../types";

const genAI = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY || "" });

export const chatWithAI = async (messages: ChatMessage[]) => {
  const model = "gemini-3-flash-preview";
  const history = messages.slice(0, -1).map(m => ({
    role: m.role,
    parts: [{ text: m.text }]
  }));
  
  const chat = genAI.chats.create({
    model,
    config: {
      systemInstruction: "你是一个专业的政策咨询专家。请根据中国各级政府发布的政策文件，为用户提供准确、客观、易懂的解答。如果用户询问具体的政策，请结合已知政策库进行回答。如果无法确定，请提示用户以官方发布为准。",
    },
  });

  // Since we don't have a real persistent history in this simple mock, 
  // we just send the last message but we could use sendMessageStream for better UX.
  const response = await chat.sendMessage({
    message: messages[messages.length - 1].text
  });

  return response.text;
};

export const interpretPolicy = async (policy: Policy) => {
  const response = await genAI.models.generateContent({
    model: "gemini-3-flash-preview",
    contents: `请对以下政策进行深度解读，包括核心要点、受惠对象、申报条件和建议措施。
    政策标题：${policy.title}
    政策内容：${policy.content}`,
  });
  return response.text;
};

export const matchPolicies = async (userInfo: any, policies: Policy[]) => {
  const policySummaries = policies.map(p => `${p.id}: ${p.title} (${p.summary})`).join('\n');
  const response = await genAI.models.generateContent({
    model: "gemini-3-flash-preview",
    contents: `根据用户信息，从以下政策列表中筛选出最匹配的3项政策，并给出匹配度（0-100）和简要建议。
    用户信息：${JSON.stringify(userInfo)}
    政策列表：
    ${policySummaries}
    
    请以JSON格式返回，格式为：[{policyId: string, score: number, suggestion: string}]`,
    config: {
      responseMimeType: "application/json"
    }
  });
  return JSON.parse(response.text || "[]");
};
