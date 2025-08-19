import React from 'react'
import {InputGroup, Menu, MenuItem, Spinner, Switch, Tooltip} from '@blueprintjs/core'
import './style.css'

export default function search(props) {
    const {
        showRc,
        toggleRc,
        requestSearch,
        searching,
    } = props
    return <div className="search-wrapper">
        <Tooltip
            content={<SearchPatternNote />}>
            <InputGroup
                large
                fill
                leftIcon="search"
                type="search"
                rightElement={
                    <div style={{ padding: '12px 8px 0 4px' }}>
                        {searching &&
                            <Spinner size={16} intent="primary" />}
                    </div>}
                onChange={(e) => {
                    requestSearch(e.target.value)
                }}
                placeholder="Component..." />

        </Tooltip>
        <Switch
            style={{paddingLeft: "8px"}}
            checked={showRc}
            onClick={toggleRc}
            label="RC"
            large
            alignIndicator="right"
        />
    </div>
}

function buildCompletionMenu(searchResult, handleComponentSelect) {
    const menuItems = searchResult.map((e) => {
        let text = `${e.componentId}:${e.version}`
        return <MenuItem
            key={text}
            icon='box'
            onClick={handleComponentSelect(e.componentId, e.version)}
            text={text}/>
    })
    return <Menu>{menuItems}</Menu>
}

const SearchPatternNote = () => {
    return <div className="pattern-note">
         Specify COMPONENT to filter
    </div>
}
