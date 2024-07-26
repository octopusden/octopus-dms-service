import {connect} from "react-redux";
import {componentsOperations} from "../duck";
import {Component} from "react";
import componentGroupPane from "./presenter.jsx"
import queryString from "query-string";
import get from "lodash/get";
import history from "../../utils/history";

const mapStateToProps = (state) => {
    const {selectedComponentGroupTab} = get(state, "components.currentArtifacts")
    return {
        selectedComponentGroupTab
    }
}

const mapDispatchToProps = (dispatch) => {
    const handleComponentGroupTabChange = (selectedComponentGroupTab) => {
        dispatch(componentsOperations.handleComponentGroupTabChange(selectedComponentGroupTab))
    }
    const getCustomComponents = (onSuccess) => {
        dispatch(componentsOperations.getCustomComponents(onSuccess))
    }
    const getClientsComponents = (onSuccess) => {
        dispatch(componentsOperations.getClientComponents(onSuccess))
    }
    return {handleComponentGroupTabChange, getCustomComponents, getClientsComponents}
}

function mergeProps(stateProps, dispatchProps, ownProps) {
    return {
        ...stateProps,
        ...dispatchProps,
        ...ownProps
    }
}

const propsToUrl = (props) => {
    const {selectedComponentGroupTab} = props
    return {
        tab: selectedComponentGroupTab == null ? undefined : selectedComponentGroupTab
    }
}

class ComponentGroupPane extends Component {
    constructor(props, context) {
        super(props, context);
        const urlProps = queryString.parse(history.location.search)
        const {tab} = urlProps
        if (tab) {
            props.selectedComponentGroupTab = tab
        }
    }

    componentDidUpdate(prev) {
        const urlState = propsToUrl(this.props)
        history.push({search: queryString.stringify(urlState)})
    }

    render() {
        return componentGroupPane(this.props)
    }
}

export default connect(mapStateToProps, mapDispatchToProps, mergeProps)(ComponentGroupPane)