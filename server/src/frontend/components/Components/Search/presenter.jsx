import React from 'react'
import {InputGroup, Menu, MenuItem, Popover, Position, Spinner, Switch} from '@blueprintjs/core'
import './style.css'

export default function search(props) {
    const {
        showRc,
        toggleRc,
        requestSearch,
        searching,
        showSearchPopover,
        handleInputFocus,
        handleInputBlur,
    } = props
    return <div className="search-wrapper">
        <Popover
            popoverClassName="completion-list"
            autoFocus={false}
            enforceFocus={false}
            content={<SearchPatternNote/>}
            isOpen={showSearchPopover && !searching}
            position={Position.BOTTOM_LEFT}>

            <InputGroup
                large
                fill
                leftIcon="search"
                type="search"
                rightElement={
                    <span style={{ paddingRight: '8px', paddingTop: '16px' }}>
                        {searching &&
                            <Spinner size={16} intent="primary" />}
                    </span>}
                onFocus={handleInputFocus}
                onBlur={handleInputBlur}
                onChange={(e) => {
                    requestSearch(e.target.value)
                }}
                placeholder="Component..."/>

        </Popover>
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

const SearchPatternNote = (props) => {
    return <div className="pattern-note">
        Specify COMPONENT to filter
    </div>
}
