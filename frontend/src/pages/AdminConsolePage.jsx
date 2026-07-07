import React, { useState } from 'react';
import TopTabs from '../components/admin/TopTabs';
import AgentsTab from '../components/admin/AgentsTab';
import KnowledgeTab from '../components/admin/KnowledgeTab';
import ToolsTab from '../components/admin/ToolsTab';
import ModelsTab from '../components/admin/ModelsTab';
import { AdminConsoleProvider } from '../components/admin/AdminConsoleFeedback';
import './AdminConsolePage.css';

const TABS = {
    agent: AgentsTab,
    knowledge: KnowledgeTab,
    tools: ToolsTab,
    models: ModelsTab
};

const AdminConsolePage = () => {
    const [activeTab, setActiveTab] = useState('agent');
    const ActiveComponent = TABS[activeTab] || AgentsTab;

    return (
        <AdminConsoleProvider>
            <div className="admin-console-page">
                <TopTabs activeTab={activeTab} onTabChange={setActiveTab} />
                <main className="admin-console-content">
                    <div key={activeTab} className="admin-tab-content">
                        <ActiveComponent />
                    </div>
                </main>
            </div>
        </AdminConsoleProvider>
    );
};

export default AdminConsolePage;
