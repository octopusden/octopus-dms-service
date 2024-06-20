import {combineReducers} from 'redux'
import componentsReducer from './components/duck/reducers'

const rootReducer = combineReducers({
    components: componentsReducer
})

export default rootReducer
