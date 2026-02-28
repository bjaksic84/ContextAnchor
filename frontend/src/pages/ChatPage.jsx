import { useState, useEffect, useRef, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { chat as chatApi, documents as docsApi } from '../api/client';
import ChatMessage from '../components/ChatMessage';
import {
  Send,
  Plus,
  Trash2,
  MessageSquare,
  Loader2,
  FileText,
  CheckSquare,
  Square,
  AlertCircle,
  Sparkles,
} from 'lucide-react';

export default function ChatPage() {
  const { conversationId } = useParams();
  const navigate = useNavigate();

  // State
  const [conversations, setConversations] = useState([]);
  const [messages, setMessages] = useState([]);
  const [question, setQuestion] = useState('');
  const [sending, setSending] = useState(false);
  const [docs, setDocs] = useState([]);
  const [selectedDocs, setSelectedDocs] = useState([]);
  const [showDocPicker, setShowDocPicker] = useState(false);
  const [error, setError] = useState('');
  const [loadingConvo, setLoadingConvo] = useState(false);

  const messagesEndRef = useRef(null);
  const inputRef = useRef(null);

  // Fetch conversations list
  const fetchConversations = useCallback(async () => {
    try {
      const data = await chatApi.listConversations();
      setConversations(data);
    } catch { /* ignore */ }
  }, []);

  // Fetch available documents
  useEffect(() => {
    docsApi.list().then((data) => {
      const ready = data.filter((d) => d.status === 'READY');
      setDocs(ready);
      // Auto-select all ready docs if none selected
      if (ready.length > 0 && selectedDocs.length === 0) {
        setSelectedDocs(ready.map((d) => d.id));
      }
    });
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // Fetch conversations on mount
  useEffect(() => {
    fetchConversations();
  }, [fetchConversations]);

  // Load conversation when ID changes
  useEffect(() => {
    if (!conversationId) {
      setMessages([]);
      return;
    }
    setLoadingConvo(true);
    chatApi
      .getConversation(conversationId)
      .then((data) => {
        setMessages(
          data.messages.map((m) => ({
            role: m.role,
            content: m.content,
            sources: null,
          }))
        );
      })
      .catch(() => {
        setMessages([]);
        navigate('/chat');
      })
      .finally(() => setLoadingConvo(false));
  }, [conversationId, navigate]);

  // Scroll to bottom on new messages
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  // Focus input
  useEffect(() => {
    inputRef.current?.focus();
  }, [conversationId]);

  const toggleDoc = (id) => {
    setSelectedDocs((prev) =>
      prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]
    );
  };

  const handleSend = async (e) => {
    e?.preventDefault();
    if (!question.trim() || sending) return;

    if (selectedDocs.length === 0) {
      setError('Please select at least one document to search');
      return;
    }

    const userMsg = question.trim();
    setQuestion('');
    setError('');
    setMessages((prev) => [...prev, { role: 'user', content: userMsg }]);
    setSending(true);

    try {
      const res = await chatApi.send(userMsg, selectedDocs, conversationId || null);
      setMessages((prev) => [
        ...prev,
        { role: 'assistant', content: res.answer, sources: res.sources },
      ]);

      // Navigate to conversation if this is a new one
      if (!conversationId && res.conversationId) {
        navigate(`/chat/${res.conversationId}`, { replace: true });
      }

      // Refresh conversations sidebar
      fetchConversations();
    } catch (err) {
      setError(err.message || 'Failed to get response');
      // Remove the optimistic user message if it failed
      setMessages((prev) => prev.slice(0, -1));
      setQuestion(userMsg);
    } finally {
      setSending(false);
      inputRef.current?.focus();
    }
  };

  const handleNewChat = () => {
    navigate('/chat');
    setMessages([]);
    setQuestion('');
    setError('');
  };

  const handleDeleteConvo = async (id, e) => {
    e.stopPropagation();
    if (!confirm('Delete this conversation?')) return;
    try {
      await chatApi.deleteConversation(id);
      setConversations((prev) => prev.filter((c) => c.id !== id));
      if (conversationId === id) {
        navigate('/chat');
        setMessages([]);
      }
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <div className="flex h-full">
      {/* Conversations sidebar */}
      <div className="w-72 flex-shrink-0 border-r bg-gray-50 flex flex-col">
        <div className="p-3 border-b">
          <button onClick={handleNewChat} className="btn-primary w-full text-sm">
            <Plus className="h-4 w-4" />
            New Chat
          </button>
        </div>

        <div className="flex-1 overflow-auto p-2 space-y-0.5">
          {conversations.length === 0 ? (
            <div className="text-center py-8 text-gray-400 text-sm">
              No conversations yet
            </div>
          ) : (
            conversations.map((convo) => (
              <div
                key={convo.id}
                onClick={() => navigate(`/chat/${convo.id}`)}
                className={`group flex items-center gap-2 rounded-lg px-3 py-2.5 cursor-pointer transition-colors ${
                  conversationId === convo.id
                    ? 'bg-brand-100 text-brand-800'
                    : 'hover:bg-gray-100 text-gray-700'
                }`}
              >
                <MessageSquare className="h-4 w-4 flex-shrink-0 opacity-60" />
                <span className="flex-1 text-sm truncate">
                  {convo.title || 'Untitled'}
                </span>
                <button
                  onClick={(e) => handleDeleteConvo(convo.id, e)}
                  className="opacity-0 group-hover:opacity-100 p-1 rounded hover:bg-red-100 text-gray-400 hover:text-red-600 transition-all"
                >
                  <Trash2 className="h-3.5 w-3.5" />
                </button>
              </div>
            ))
          )}
        </div>
      </div>

      {/* Chat area */}
      <div className="flex-1 flex flex-col">
        {/* Messages */}
        <div className="flex-1 overflow-auto px-6 py-6 space-y-6">
          {loadingConvo ? (
            <div className="flex justify-center py-12">
              <Loader2 className="h-6 w-6 animate-spin text-brand-500" />
            </div>
          ) : messages.length === 0 ? (
            <div className="flex flex-col items-center justify-center h-full text-center">
              <div className="rounded-2xl bg-brand-50 p-4 mb-4">
                <Sparkles className="h-10 w-10 text-brand-500" />
              </div>
              <h2 className="text-xl font-semibold text-gray-900 mb-2">
                Start a conversation
              </h2>
              <p className="text-gray-500 max-w-md text-sm leading-relaxed">
                Ask questions about your uploaded documents. The AI will search through your
                documents and provide answers grounded in your data with source citations.
              </p>
              {docs.length === 0 && (
                <p className="mt-4 text-sm text-amber-600 flex items-center gap-1.5">
                  <AlertCircle className="h-4 w-4" />
                  No documents available — upload some first
                </p>
              )}
            </div>
          ) : (
            <>
              {messages.map((msg, i) => (
                <ChatMessage key={i} {...msg} />
              ))}
              {sending && (
                <div className="flex gap-3">
                  <div className="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full bg-gray-200 text-gray-700 text-sm font-semibold">
                    AI
                  </div>
                  <div className="rounded-2xl rounded-tl-md bg-gray-100 px-4 py-3">
                    <div className="flex gap-1.5">
                      <span className="h-2 w-2 rounded-full bg-gray-400 animate-bounce" style={{ animationDelay: '0ms' }} />
                      <span className="h-2 w-2 rounded-full bg-gray-400 animate-bounce" style={{ animationDelay: '150ms' }} />
                      <span className="h-2 w-2 rounded-full bg-gray-400 animate-bounce" style={{ animationDelay: '300ms' }} />
                    </div>
                  </div>
                </div>
              )}
              <div ref={messagesEndRef} />
            </>
          )}
        </div>

        {/* Error */}
        {error && (
          <div className="mx-6 mb-2 rounded-lg bg-red-50 border border-red-200 px-4 py-2 text-sm text-red-700 flex items-center gap-2">
            <AlertCircle className="h-4 w-4 flex-shrink-0" />
            {error}
            <button onClick={() => setError('')} className="ml-auto text-xs font-medium text-red-500 hover:text-red-700">
              Dismiss
            </button>
          </div>
        )}

        {/* Input area */}
        <div className="border-t bg-white px-6 py-4">
          {/* Document selector */}
          <div className="mb-3">
            <button
              onClick={() => setShowDocPicker(!showDocPicker)}
              className="text-xs font-medium text-gray-500 hover:text-gray-700 flex items-center gap-1.5 transition-colors"
            >
              <FileText className="h-3.5 w-3.5" />
              {selectedDocs.length} of {docs.length} document{docs.length !== 1 ? 's' : ''} selected
            </button>
            {showDocPicker && docs.length > 0 && (
              <div className="mt-2 card p-2 max-h-40 overflow-auto space-y-0.5">
                {docs.map((doc) => (
                  <button
                    key={doc.id}
                    onClick={() => toggleDoc(doc.id)}
                    className="flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-sm text-left hover:bg-gray-50 transition-colors"
                  >
                    {selectedDocs.includes(doc.id) ? (
                      <CheckSquare className="h-4 w-4 text-brand-600 flex-shrink-0" />
                    ) : (
                      <Square className="h-4 w-4 text-gray-300 flex-shrink-0" />
                    )}
                    <span className="truncate text-gray-700">{doc.originalName}</span>
                    <span className="ml-auto text-xs text-gray-400">{doc.chunkCount} chunks</span>
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* Message input */}
          <form onSubmit={handleSend} className="flex gap-3">
            <input
              ref={inputRef}
              type="text"
              className="input flex-1"
              placeholder={
                docs.length === 0
                  ? 'Upload documents first…'
                  : 'Ask a question about your documents…'
              }
              value={question}
              onChange={(e) => setQuestion(e.target.value)}
              disabled={sending || docs.length === 0}
            />
            <button
              type="submit"
              className="btn-primary px-4"
              disabled={sending || !question.trim() || selectedDocs.length === 0}
            >
              {sending ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <Send className="h-4 w-4" />
              )}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}
