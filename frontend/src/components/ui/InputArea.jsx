import React, { useState, useRef, useEffect } from 'react';
import { Send, Paperclip, Mic, Globe, Calculator, X, Image, FileText } from 'lucide-react';
import '../ui/chat-ui.css';

const InputArea = ({ onSend, disabled, isGenerating }) => {
    const [text, setText] = useState('');
    const [showAttachMenu, setShowAttachMenu] = useState(false);
    const [attachedFiles, setAttachedFiles] = useState([]);
    const [isListening, setIsListening] = useState(false);
    const [webSearchEnabled, setWebSearchEnabled] = useState(false);
    const [calcEnabled, setCalcEnabled] = useState(false);
    const textareaRef = useRef(null);
    const fileInputRef = useRef(null);
    const recognitionRef = useRef(null);
    const attachWrapperRef = useRef(null);

    const autoResize = () => {
        const el = textareaRef.current;
        if (el) {
            el.style.height = 'auto';
            el.style.height = Math.min(el.scrollHeight, 200) + 'px';
        }
    };

    useEffect(() => {
        autoResize();
    }, [text]);

    const handleSubmit = (e) => {
        e?.preventDefault();
        if ((text.trim() || attachedFiles.length > 0) && !disabled && !isGenerating) {
            onSend(text, attachedFiles, {
                webSearch: webSearchEnabled,
                calculate: calcEnabled
            });
            setText('');
            setAttachedFiles([]);
        }
    };

    const handleKeyDown = (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            handleSubmit();
        }
    };

    const handleFileSelect = (e, type) => {
        const files = Array.from(e.target.files || []);
        if (files.length > 0) {
            const newFiles = files.map((file) => ({
                file,
                type,
                name: file.name,
                preview: type === 'image' ? URL.createObjectURL(file) : null
            }));
            setAttachedFiles((prev) => [...prev, ...newFiles]);
        }
        setShowAttachMenu(false);
        if (fileInputRef.current) {
            fileInputRef.current.value = '';
        }
    };

    const removeFile = (index) => {
        setAttachedFiles((prev) => {
            const newFiles = [...prev];
            if (newFiles[index].preview) {
                URL.revokeObjectURL(newFiles[index].preview);
            }
            newFiles.splice(index, 1);
            return newFiles;
        });
    };

    const stopListening = () => {
        if (recognitionRef.current) {
            recognitionRef.current.stop();
            recognitionRef.current = null;
        }
        setIsListening(false);
    };

    const handleVoiceInput = () => {
        if (disabled || isGenerating) return;
        if (isListening) {
            stopListening();
            return;
        }

        const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
        if (!SpeechRecognition) {
            window.alert('当前浏览器暂不支持语音输入，请使用最新版 Chrome。');
            return;
        }

        const recognition = new SpeechRecognition();
        recognition.lang = 'zh-CN';
        recognition.interimResults = true;
        recognition.continuous = false;
        let finalTranscript = '';

        recognition.onresult = (event) => {
            for (let i = event.resultIndex; i < event.results.length; i += 1) {
                const transcript = event.results[i][0]?.transcript || '';
                if (event.results[i].isFinal) {
                    finalTranscript += transcript;
                }
            }
        };

        recognition.onend = () => {
            setIsListening(false);
            recognitionRef.current = null;
            const transcript = finalTranscript.trim();
            if (!transcript) return;
            setText((prev) => {
                const base = prev.trim();
                return base ? `${base} ${transcript}` : transcript;
            });
        };

        recognition.onerror = () => {
            setIsListening(false);
            recognitionRef.current = null;
        };

        recognitionRef.current = recognition;
        setIsListening(true);
        recognition.start();
    };

    useEffect(() => () => {
        if (recognitionRef.current) {
            recognitionRef.current.stop();
            recognitionRef.current = null;
        }
    }, []);

    useEffect(() => {
        if (!showAttachMenu) return undefined;

        const handleOutsideClick = (event) => {
            if (!attachWrapperRef.current) return;
            if (!attachWrapperRef.current.contains(event.target)) {
                setShowAttachMenu(false);
            }
        };

        const handleEscape = (event) => {
            if (event.key === 'Escape') setShowAttachMenu(false);
        };

        document.addEventListener('mousedown', handleOutsideClick);
        document.addEventListener('touchstart', handleOutsideClick);
        document.addEventListener('keydown', handleEscape);

        return () => {
            document.removeEventListener('mousedown', handleOutsideClick);
            document.removeEventListener('touchstart', handleOutsideClick);
            document.removeEventListener('keydown', handleEscape);
        };
    }, [showAttachMenu]);

    const canSend = (text.trim() || attachedFiles.length > 0) && !disabled && !isGenerating;

    return (
        <div className="ui-input-zone">
            <div className="ui-input-container">
                {attachedFiles.length > 0 && (
                    <div className="ui-attached-files">
                        {attachedFiles.map((file, index) => (
                            <div key={index} className="ui-attached-file">
                                {file.type === 'image' ? (
                                    <img src={file.preview} alt={file.name} />
                                ) : (
                                    <FileText size={16} style={{ color: 'var(--ui-primary)' }} />
                                )}
                                <span className="ui-attached-file-name">{file.name}</span>
                                <button
                                    type="button"
                                    className="ui-attached-file-remove"
                                    onClick={() => removeFile(index)}
                                >
                                    <X size={12} />
                                </button>
                            </div>
                        ))}
                    </div>
                )}

                <textarea
                    ref={textareaRef}
                    className="ui-input-textarea"
                    placeholder={isGenerating ? '正在生成回答...' : '输入您的问题，例如：汽车以旧换新补贴多少？'}
                    value={text}
                    onChange={(e) => setText(e.target.value)}
                    onKeyDown={handleKeyDown}
                    disabled={disabled || isGenerating}
                    rows={1}
                />

                <div className="ui-input-toolbar">
                    <div className="ui-input-toolbar-left">
                        <button
                            type="button"
                            className={`ui-tool-toggle ${webSearchEnabled ? 'active' : ''}`}
                            onClick={() => setWebSearchEnabled(!webSearchEnabled)}
                            disabled={disabled || isGenerating}
                        >
                            <Globe size={13} />
                            联网搜索
                        </button>
                        <button
                            type="button"
                            className={`ui-tool-toggle ${calcEnabled ? 'active' : ''}`}
                            onClick={() => setCalcEnabled(!calcEnabled)}
                            disabled={disabled || isGenerating}
                        >
                            <Calculator size={13} />
                            补贴计算
                        </button>
                    </div>

                    <div className="ui-input-toolbar-right">
                        <div ref={attachWrapperRef} style={{ position: 'relative' }}>
                            <button
                                type="button"
                                className="ui-tool-btn"
                                onClick={() => setShowAttachMenu(!showAttachMenu)}
                                disabled={disabled || isGenerating}
                                title="上传附件"
                            >
                                <Paperclip size={16} />
                            </button>

                            {showAttachMenu && (
                                <div className="ui-attach-menu">
                                    <label className="ui-attach-option">
                                        <Image size={16} />
                                        <span>图片</span>
                                        <input
                                            type="file"
                                            ref={fileInputRef}
                                            accept="image/*"
                                            multiple
                                            onChange={(e) => handleFileSelect(e, 'image')}
                                            hidden
                                        />
                                    </label>
                                    <label className="ui-attach-option">
                                        <FileText size={16} />
                                        <span>PDF</span>
                                        <input
                                            type="file"
                                            accept=".pdf"
                                            multiple
                                            onChange={(e) => handleFileSelect(e, 'pdf')}
                                            hidden
                                        />
                                    </label>
                                </div>
                            )}
                        </div>

                        <button
                            type="button"
                            className={`ui-tool-btn ${isListening ? 'active' : ''}`}
                            onClick={handleVoiceInput}
                            disabled={disabled || isGenerating}
                            title={isListening ? '停止语音输入' : '语音输入'}
                        >
                            <Mic size={16} />
                        </button>

                        <button
                            type="button"
                            className="ui-send-btn"
                            onClick={handleSubmit}
                            disabled={!canSend}
                            title="发送"
                        >
                            <Send size={15} />
                        </button>
                    </div>
                </div>
            </div>
            <div className="ui-input-footer">AI 生成内容仅供参考，具体政策以官方文件为准</div>
        </div>
    );
};

export default InputArea;
