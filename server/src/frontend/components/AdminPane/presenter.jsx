import React from 'react'
import {Switch} from '@blueprintjs/core'
import './style.css'

export default function adminPane(props) {
    let {adminMode, toggleAdminMode} = props

    return <div className="admin-pane-wrapper">
        <Switch className="admin-switch"
                checked={adminMode}
                onClick={toggleAdminMode}
                label="Admin mode"
                large={false}
                alignIndicator="right"
        />
    </div>
}