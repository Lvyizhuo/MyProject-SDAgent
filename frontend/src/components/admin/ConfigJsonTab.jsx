import React from 'react';
import './ConfigJsonTab.css';

const ConfigJsonTab = ({ config }) => {
    return (
        <div className="config-json-tab">
            <pre>
                <code>
                    {JSON.stringify(config, null, 2)}
                </code>
            </pre>
        </div>
    );
};

export default ConfigJsonTab;