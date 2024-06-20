import React, {Component} from 'react'
import {InputGroup, Menu, MenuItem, Popover, Position, Spinner, Switch} from '@blueprintjs/core'
import './Search.css'

export default class Search extends Component {
    constructor(props) {
        super(props)
        this.state = {showSearchPopover: false}
    }

    handleInputFocus = () => {
        this.setState({showSearchPopover: true})
    }

    handleInputBlur = () => {
        setTimeout(() => this.setState({showSearchPopover: false}), 600)
    }

    handleComponentSelect = (component, version) => () => {
        console.log(component, version)
        const {fetchComponentVersions, fetchArtifactsList} = this.props

        fetchComponentVersions(component)
        fetchArtifactsList(component, version)
    }

    render() {
        let {
            showRc, toggleRc, requestSearch, searchResult, searching,
            searchQueryValid
        } = this.props
        let {showSearchPopover} = this.state
        let content = searchQueryValid && searchResult.length > 0 ?
            buildCompletionMenu(searchResult, this.handleComponentSelect) :
            <SearchPatternNote/>
        return <div className="search-wrapper">
            <Popover
                popoverClassName="completion-list"
                autoFocus={false}
                enforceFocus={false}
                content={content}
                isOpen={showSearchPopover}
                position={Position.BOTTOM_LEFT}>

                <InputGroup
                    disabled
                    large
                    fill
                    leftIcon="search"
                    type="search"
                    rightElement={searching && <Spinner size={16} intent="primary"/>}
                    onFocus={this.handleInputFocus}
                    onBlur={this.handleInputBlur}
                    onChange={(e) => {
                        requestSearch(e.target.value)
                    }}
                    placeholder="Component version..."/>

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
}

function buildCompletionMenu(searchResult, handleComponentSelect) {
    let menuItems = searchResult.map((e) => {
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
        Type query with pattern: "COMPONENT VERSION"
    </div>
}
