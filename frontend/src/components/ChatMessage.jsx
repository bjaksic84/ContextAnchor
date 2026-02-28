import { BookOpen, ChevronDown, ChevronUp } from 'lucide-react';
import { useState } from 'react';

export default function ChatMessage({ role, content, sources }) {
  const [showSources, setShowSources] = useState(false);
  const isUser = role === 'user';

  return (
    <div className={`flex gap-3 ${isUser ? 'flex-row-reverse' : ''}`}>
      {/* Avatar */}
      <div
        className={`flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full text-sm font-semibold ${
          isUser ? 'bg-brand-600 text-white' : 'bg-gray-200 text-gray-700'
        }`}
      >
        {isUser ? 'U' : 'AI'}
      </div>

      {/* Bubble */}
      <div className={`max-w-[75%] space-y-2 ${isUser ? 'items-end' : ''}`}>
        <div
          className={`rounded-2xl px-4 py-3 text-sm leading-relaxed whitespace-pre-wrap ${
            isUser
              ? 'bg-brand-600 text-white rounded-tr-md'
              : 'bg-gray-100 text-gray-800 rounded-tl-md'
          }`}
        >
          {content}
        </div>

        {/* Sources accordion */}
        {sources && sources.length > 0 && (
          <div className="rounded-lg border border-gray-200 bg-white overflow-hidden">
            <button
              onClick={() => setShowSources(!showSources)}
              className="flex w-full items-center gap-2 px-3 py-2 text-xs font-medium text-gray-600 hover:bg-gray-50 transition-colors"
            >
              <BookOpen className="h-3.5 w-3.5" />
              {sources.length} source{sources.length !== 1 ? 's' : ''}
              {showSources ? <ChevronUp className="h-3.5 w-3.5 ml-auto" /> : <ChevronDown className="h-3.5 w-3.5 ml-auto" />}
            </button>
            {showSources && (
              <div className="border-t divide-y">
                {sources.map((src, i) => (
                  <div key={i} className="px-3 py-2.5">
                    <div className="flex items-center gap-2 text-xs font-medium text-gray-700 mb-1">
                      <span className="flex h-5 w-5 items-center justify-center rounded bg-brand-100 text-brand-700 text-[10px] font-bold">
                        {i + 1}
                      </span>
                      <span className="truncate">{src.documentName}</span>
                      <span className="text-gray-400">Chunk {src.chunkIndex}</span>
                      {src.similarityScore && (
                        <span className="ml-auto badge bg-emerald-100 text-emerald-700">
                          {(src.similarityScore * 100).toFixed(0)}% match
                        </span>
                      )}
                    </div>
                    <p className="text-xs text-gray-500 line-clamp-2">{src.chunkContent}</p>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
