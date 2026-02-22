import React, { useEffect, useRef, useState } from 'react';
import { Send, Plus, X, FileText, Image, Mic } from 'lucide-react';
import './InputArea.css';

const InputArea = ({ onSend, disabled, isGenerating }) => {
    const [text, setText] = useState('');
    const [showAttachMenu, setShowAttachMenu] = useState(false);
    const [attachedFiles, setAttachedFiles] = useState([]);
    const [isListening, setIsListening] = useState(false);
    const fileInputRef = useRef(null);
    const recognitionRef = useRef(null);
    const attachWrapperRef = useRef(null);

    const handleSubmit = (e) => {
        e.preventDefault();
        if ((text.trim() || attachedFiles.length > 0) && !disabled && !isGenerating) {
            onSend(text, attachedFiles);
            setText('');
            setAttachedFiles([]);
        }
    };

    const handleFileSelect = (e, type) => {
        const files = Array.from(e.target.files || []);
        if (files.length > 0) {
            const newFiles = files.map(file => ({
                file,
                type,
                name: file.name,
                preview: type === 'image' ? URL.createObjectURL(file) : null
            }));
            setAttachedFiles(prev => [...prev, ...newFiles]);
        }
        setShowAttachMenu(false);
        if (fileInputRef.current) {
            fileInputRef.current.value = '';
        }
    };

    const removeFile = (index) => {
        setAttachedFiles(prev => {
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
        if (disabled || isGenerating) {
            return;
        }

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
            if (!transcript) {
                return;
            }
            setText(prev => {
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
        if (!showAttachMenu) {
            return undefined;
        }

        const handleOutsideClick = (event) => {
            if (!attachWrapperRef.current) {
                return;
            }
            if (!attachWrapperRef.current.contains(event.target)) {
                setShowAttachMenu(false);
            }
        };

        const handleEscape = (event) => {
            if (event.key === 'Escape') {
                setShowAttachMenu(false);
            }
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

    return (
        <div className="input-area-wrapper">
            {attachedFiles.length > 0 && (
                <div className="attached-files">
                    {attachedFiles.map((file, index) => (
                        <div key={index} className="attached-file">
                            {file.type === 'image' ? (
                                <img src={file.preview} alt={file.name} className="file-preview" />
                            ) : (
                                <div className="file-icon">
                                    <FileText size={20} />
                                </div>
                            )}
                            <span className="file-name">{file.name}</span>
                            <button 
                                type="button" 
                                className="remove-file-btn"
                                onClick={() => removeFile(index)}
                            >
                                <X size={14} />
                            </button>
                        </div>
                    ))}
                </div>
            )}
            
            <form className="input-area-container" onSubmit={handleSubmit}>
                <div className="attach-wrapper" ref={attachWrapperRef}>
                    <button
                        type="button"
                        className="icon-btn attach-btn"
                        onClick={() => setShowAttachMenu(!showAttachMenu)}
                        disabled={disabled || isGenerating}
                    >
                        <Plus size={22} />
                    </button>
                    
                    {showAttachMenu && (
                        <div className="attach-menu">
                            <label className="attach-option">
                                <Image size={18} />
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
                            <label className="attach-option">
                                <FileText size={18} />
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

                <input
                    type="text"
                    className="input-field"
                    placeholder={isGenerating ? "正在生成回答..." : "输入关于以旧换新政策的问题..."}
                    value={text}
                    onChange={(e) => setText(e.target.value)}
                    disabled={disabled || isGenerating}
                />

                <button
                    type="button"
                    className={`icon-btn voice-btn ${isListening ? 'active' : ''}`}
                    onClick={handleVoiceInput}
                    disabled={disabled || isGenerating}
                    title={isListening ? '停止语音输入' : '语音输入'}
                >
                    <Mic size={18} />
                </button>

                <button
                    type="submit"
                    className={`icon-btn send-btn ${(!text.trim() && attachedFiles.length === 0) || disabled || isGenerating ? 'disabled' : ''}`}
                    disabled={(!text.trim() && attachedFiles.length === 0) || disabled || isGenerating}
                >
                    <Send size={18} />
                </button>
            </form>
        </div>
    );
};

export default InputArea;
