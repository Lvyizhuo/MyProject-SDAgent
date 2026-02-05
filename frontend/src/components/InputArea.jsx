import React, { useState, useRef } from 'react';
import { Send, StopCircle, Plus, Mic, X, FileText, Image } from 'lucide-react';
import './InputArea.css';

const InputArea = ({ onSend, disabled, isGenerating }) => {
    const [text, setText] = useState('');
    const [showAttachMenu, setShowAttachMenu] = useState(false);
    const [attachedFiles, setAttachedFiles] = useState([]);
    const fileInputRef = useRef(null);

    const handleSubmit = (e) => {
        e.preventDefault();
        if ((text.trim() || attachedFiles.length > 0) && !disabled) {
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

    const handleVoiceInput = () => {
        if (!('webkitSpeechRecognition' in window) && !('SpeechRecognition' in window)) {
            alert('您的浏览器不支持语音输入功能');
            return;
        }

        const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
        const recognition = new SpeechRecognition();
        recognition.lang = 'zh-CN';
        recognition.continuous = false;
        recognition.interimResults = false;

        recognition.onresult = (event) => {
            const transcript = event.results[0][0].transcript;
            setText(prev => prev + transcript);
        };

        recognition.onerror = (event) => {
            console.error('语音识别错误:', event.error);
            if (event.error === 'not-allowed') {
                alert('请允许麦克风权限以使用语音输入');
            }
        };

        recognition.start();
    };

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
                <div className="attach-wrapper">
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
                    className="icon-btn voice-btn"
                    onClick={handleVoiceInput}
                    disabled={disabled || isGenerating}
                    title="语音输入"
                >
                    <Mic size={20} />
                </button>

                <button
                    type="submit"
                    className={`icon-btn send-btn ${(!text.trim() && attachedFiles.length === 0) || disabled ? 'disabled' : ''}`}
                    disabled={(!text.trim() && attachedFiles.length === 0) || disabled}
                >
                    {isGenerating ? <StopCircle size={20} /> : <Send size={20} />}
                </button>
            </form>
        </div>
    );
};

export default InputArea;
