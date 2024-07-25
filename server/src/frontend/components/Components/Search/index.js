import get from "lodash/get";
import {Component} from "react";
import search from "./presenter.jsx";
import {connect} from "react-redux";
import {componentsOperations} from "../../duck";

const mapStateToProps = (state) => {
    const {selectedComponent, selectedComponentName, selectedVersion} = get(state, "components.currentArtifacts")

    const {
        showRc, searchQueryValid, searchResult, searching, showSearchPopover
    } = state.components

    return {
        selectedComponent,
        selectedComponentName,
        selectedVersion,
        showRc,
        searchResult,
        searchQueryValid,
        searching,
        showSearchPopover
    }
}

const mapDispatchToProps = (dispatch) => {
    const selectVersion = (componentId, minorVersion, version) => {
        dispatch(componentsOperations.selectVersion(componentId, minorVersion, version))
    }
    const toggleRc = () => {
        dispatch(componentsOperations.toggleRc())
    }
    const requestSearch = (query) => {
        dispatch(componentsOperations.requestSearch(query))
    }
    const getComponentVersions = (componentId, minorVersion) => {
        dispatch(componentsOperations.getComponentVersions(componentId, minorVersion))
    }
    const handleInputFocus = () => {
        // this.setState({showSearchPopover: true})
    }
    const handleInputBlur = () => {
        // setTimeout(() => this.setState({showSearchPopover: false}), 600)
    }
    const handleComponentSelect = (component, version) => () => {
        console.log(component, version)
        const {fetchArtifactsList} = this.props

        getComponentVersions(component)
        fetchArtifactsList(component, version)
    }

    return {selectVersion, toggleRc, requestSearch, getComponentVersions, handleInputFocus, handleInputBlur, handleComponentSelect}
}

const mergeProps = (stateProps, dispatchProps, ownProps) => {
    return {
        ...stateProps,
        ...dispatchProps,
        ...ownProps
    }
}

class Search extends Component {
    constructor(props) {
        super(props)
        this.state = {showSearchPopover: false}
    }

    render() {
        return search(this.props)
    }
}

export default connect(mapStateToProps, mapDispatchToProps, mergeProps)(Search)