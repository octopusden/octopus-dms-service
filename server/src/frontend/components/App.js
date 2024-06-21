import React, {Component} from 'react'
import {connect} from 'react-redux'
import ComponentsComponent from './Components'
import {componentsOperations} from './duck'
import get from "lodash/get";

const mapStateToProps = (state) => {
    const {buildInfo, loggedUser} = get(state, "components")
    return {
        buildInfo, loggedUser
    }
}

const mapDispatchToProps = (dispatch) => {
    const fetchBuildInfo = () => {
        dispatch(componentsOperations.getBuildInfo())
    }
    const fetchLoggedUser = () => {
        dispatch(componentsOperations.getLoggedUser())
    }
    const showError = (errorMessage) => {
        dispatch(componentsOperations.showError(errorMessage))
    }
    const hideError = () => {
        dispatch(componentsOperations.hideError())
    }
    const requestSearch = (query) => {
        dispatch(componentsOperations.requestSearch(query))
    }
    const showConfirmation = (message, onConfirm) => {
        dispatch(componentsOperations.showConfirmation(message, onConfirm))
    }
    const hideConfirmation = () => {
        dispatch(componentsOperations.hideConfirmation())
    }

    return {
        fetchBuildInfo,
        fetchLoggedUser,
        showError,
        hideError,
        requestSearch,
        showConfirmation,
        hideConfirmation
    }
}

class App extends Component {

    componentDidMount() {
        const {fetchBuildInfo, fetchLoggedUser} = this.props
        fetchBuildInfo()
        fetchLoggedUser()
    }

    render() {
        return <ComponentsComponent {...this.props}/>
    }
}

export default connect(mapStateToProps, mapDispatchToProps)(App)
