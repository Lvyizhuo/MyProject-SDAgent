import React from 'react';
import { Outlet } from 'react-router-dom';
import TopNavbar from './TopNavbar';

const MainLayout = () => {
    return (
        <div className="main-layout">
            <TopNavbar />
            <Outlet />
        </div>
    );
};

export default MainLayout;
