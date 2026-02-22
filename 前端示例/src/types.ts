export interface Policy {
  id: string;
  title: string;
  agency: string;
  date: string;
  type: string;
  region: string;
  industry: string;
  summary: string;
  content: string;
}

export interface Interpretation {
  policyId: string;
  analysis: string;
  highlights: string[];
}

export interface MatchResult {
  policyId: string;
  score: number;
  suggestion: string;
}

export interface ChatMessage {
  role: 'user' | 'model';
  text: string;
  timestamp: number;
}

export interface UserProfile {
  name: string;
  type: 'individual' | 'enterprise';
  industry?: string;
  region?: string;
  scale?: string;
}
